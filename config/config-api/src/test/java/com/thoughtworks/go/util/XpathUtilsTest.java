/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

public class XpathUtilsTest {
    @TempDir
    public File temporaryFolder;

    private File testFile;
    private static final String XML = """
        <root>
        <son>
        <grandson name="someone"/>
        <grandson name="anyone" address=""></grandson>
        </son>
        </root>""";

    @AfterEach
    public void tearDown() {
        if (testFile != null && testFile.exists()) {
            testFile.delete();
        }
    }

    @Test
    public void shouldEvaluateXpath() throws Exception {
        String xpath = "/root/son/grandson/@name";
        String value = XpathUtils.evaluate(getTestFile(), xpath);
        assertThat(value).isEqualTo("someone");
    }

    @Test
    public void shouldEvaluateAnotherXpath() throws Exception {
        String xpath = "//son/grandson[2]/@name";
        String value = XpathUtils.evaluate(getTestFile(), xpath);
        assertThat(value).isEqualTo("anyone");
    }

    @Test
    public void shouldEvaluateTextValueXpath() throws Exception {
        String xpath = "//son/grandson[2]/text()";
        String value = XpathUtils.evaluate(getTestFile(), xpath);
        assertThat(value).isEmpty();
    }

    @Test
    public void shouldThrowExceptionForIllegalXpath() {
        assertThrows(XPathExpressionException.class, () -> XpathUtils.evaluate(getTestFile(), "//"));
    }

    @Test
    public void shouldCheckIfNodeExists() throws Exception {
        String attribute = "//son/grandson[@name=\"anyone\"]/@address";
        assertThat(XpathUtils.evaluate(getTestFile(), attribute)).isEmpty();
        assertThat(XpathUtils.nodeExists(getTestFile(), attribute)).isTrue();

        String textNode = "//son/grandson[2]/text()";
        assertThat(XpathUtils.nodeExists(getTestFile(), textNode)).isFalse();
    }

    @Test
    public void shouldThrowExceptionForBadXML() {
        String attribute = "//badxpath";
        try {
            XpathUtils.evaluate(getTestFile("NOT XML"), attribute);
            fail("Should throw exception if xml is valid");
        } catch (Exception ignored) {
        }
    }

    @Test
    void shouldRejectXmlContainingDocTypeDeclarationWithEntityReferenceToPreventXXE() throws Exception {
        String maliciousXml = """
            <?xml version="1.0"?>
            <!DOCTYPE root [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <root>
            <son>
            <grandson name="&xxe;"/>
            </son>
            </root>""";

        File file = getTestFile(maliciousXml);

        assertThatThrownBy(() -> XpathUtils.evaluate(file, "/root/son/grandson/@name"))
            .isExactlyInstanceOf(XPathExpressionException.class)
            .hasCauseExactlyInstanceOf(SAXParseException.class)
            .hasMessageContaining("The external entity reference ")
            .hasMessageContaining("is not permitted in an attribute value.");
    }

    @Test
    void shouldRejectXmlWithEntityReferenceButMissingDocTypeDeclaration() throws Exception {
        String maliciousXml = """
            <?xml version="1.0"?>
            <root>
            <son>
            <grandson name="&xxe;"/>
            </son>
            </root>""";

        File file = getTestFile(maliciousXml);

        assertThatThrownBy(() -> XpathUtils.evaluate(file, "/root/son/grandson/@name"))
            .isExactlyInstanceOf(XPathExpressionException.class)
            .hasCauseExactlyInstanceOf(SAXParseException.class)
            .hasMessageContaining("The entity ")
            .hasMessageContaining(" was referenced, but not declared.");
    }

    @Test
    void shouldNotRejectXmlContainingDocTypeDeclarationButMissingEntityReference() throws Exception {
        String maliciousXml = """
            <?xml version="1.0"?>
            <!DOCTYPE root [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <root>
            <son>
            <grandson name="Spiderman"/>
            </son>
            </root>""";

        File file = getTestFile(maliciousXml);

        assertDoesNotThrow(() -> XpathUtils.evaluate(file, "/root/son/grandson/@name"));
    }

    @Test
    void shouldNotReject() throws Exception {
        String maliciousXml = """
            <?xml version="1.0"?>
            <root>
            <son>
            <grandson>no-xxe</grandson>
            </son>
            </root>""";

        File file = getTestFile(maliciousXml);

        assertDoesNotThrow(() -> XpathUtils.evaluate(file, "/root/son/grandson/@name"));
    }

    @Test
    void shouldRejectNetworkCallingByDomainName() throws Exception {
        String maliciousXml = """
            <?xml version="1.0"?>
            <!DOCTYPE root [
              <!ENTITY xxe SYSTEM "https://www.google.com/">
            ]>
            <root>
                <son>
                    <grandson name="">&xxe;</grandson>
                </son>
            </root>""";

        File file = getTestFile(maliciousXml);

        assertThatThrownBy(() -> XpathUtils.evaluate(file, "/root/son/grandson/@name"))
            .isExactlyInstanceOf(XPathExpressionException.class)
            .hasCauseExactlyInstanceOf(SAXParseException.class)
            .hasMessageContaining("External Entity: Failed to read external document '', because ")
            .hasMessageContaining(" access is not allowed due to restriction set by the accessExternalDTD property.");
    }

    @Test
    void shouldRejectNetworkCallingIfURLHasNoScheme() throws Exception {
        String maliciousXml = """
            <?xml version="1.0"?>
            <!DOCTYPE root [
              <!ENTITY xxe SYSTEM "localhost:8000">
            ]>
            <root>
                <son>
                    <grandson name="name">&xxe;</grandson>
                </son>
            </root>""";

        File file = getTestFile(maliciousXml);

        assertThatThrownBy(() -> XpathUtils.evaluate(file, "/root/son/grandson/@name"))
            .isExactlyInstanceOf(XPathExpressionException.class)
            .hasCauseExactlyInstanceOf(MalformedURLException.class)
            .hasMessageContaining("java.net.MalformedURLException: unknown protocol: localhost");
    }

    @Test
    void shouldRejectNetworkCallingIfURLHasUnicodeCharacters() throws Exception {
        String maliciousXml = """
            <?xml version="1.0"?>
            <!DOCTYPE root [
              <!ENTITY xxe SYSTEM "🙂localhost:8000">
            ]>
            <root>
                <son>
                    <grandson name="name">&xxe;</grandson>
                </son>
            </root>""";

        File file = getTestFile(maliciousXml);

        assertThatThrownBy(() -> XpathUtils.evaluate(file, "/root/son/grandson/@name"))
            .isExactlyInstanceOf(XPathExpressionException.class)
            .hasCauseExactlyInstanceOf(SAXParseException.class)
            .hasMessageContaining("An invalid XML character (")
            .hasMessageContaining(") was found in the system identifier.");
    }

    @Test
    void shouldRejectNetworkCallingByLocalHostWithScheme() throws Exception {
        String maliciousXml = """
            <?xml version="1.0"?>
            <!DOCTYPE root [
              <!ENTITY xxe SYSTEM "http://localhost:8000">
            ]>
            <root>
                <son>
                    <grandson name="name">&xxe;</grandson>
                </son>
            </root>""";

        File file = getTestFile(maliciousXml);

        assertThatThrownBy(() -> XpathUtils.evaluate(file, "/root/son/grandson/@name"))
            .isExactlyInstanceOf(XPathExpressionException.class)
            .hasCauseExactlyInstanceOf(SAXParseException.class)
            .hasMessageContaining("not allowed due to restriction set by the accessExternalDTD property");
    }

    @Test
    void shouldRejectNetworkCallingByIPAddress() throws Exception {
        String maliciousXml = """
            <?xml version="1.0"?>
            <!DOCTYPE root [
              <!ENTITY xxe SYSTEM "https://8.8.8.8">
            ]>
            <root>
                <son>
                    <grandson name="name">&xxe;</grandson>
                </son>
            </root>""";

        File file = getTestFile(maliciousXml);

        assertThatThrownBy(() -> XpathUtils.evaluate(file, "/root/son/grandson/@name"))
            .isExactlyInstanceOf(XPathExpressionException.class)
            .hasCauseExactlyInstanceOf(SAXParseException.class)
            .hasMessageContaining("External Entity: Failed to read external document '")
            .hasMessageContaining("', because '")
            .hasMessageContaining("access is not allowed due to restriction set by the accessExternalDTD property.");
    }

    @Test
    void shouldRejectNetworkCallingByIPAddressWithNoScheme() throws Exception {
        String maliciousXml = """
            <?xml version="1.0"?>
            <!DOCTYPE root [
              <!ENTITY xxe SYSTEM "8.8.8.8">
            ]>
            <root>
                <son>
                    <grandson name="name">&xxe;</grandson>
                </son>
            </root>""";

        File file = getTestFile(maliciousXml);

        assertThatThrownBy(() -> XpathUtils.evaluate(file, "/root/son/grandson/@name"))
            .isExactlyInstanceOf(XPathExpressionException.class)
            .hasCauseExactlyInstanceOf(SAXParseException.class)
            .hasMessageContaining("External Entity: Failed to read external document '")
            .hasMessageContaining("', because 'file'")
            .hasMessageContaining(" access is not allowed due to restriction set by the accessExternalDTD property.");
    }

    @Test
    void shouldRejectEntityExpansion() throws Exception {
        String maliciousXml = """
            <!DOCTYPE root [
              <!ENTITY a0 "aaaaaaaaaaaaaaaaaaaa">
              <!ENTITY a1 "&a0;&a0;&a0;&a0;&a0;&a0;&a0;&a0;&a0;&a0;">
              <!ENTITY a2 "&a1;&a1;&a1;&a1;&a1;&a1;&a1;&a1;&a1;&a1;">
              <!ENTITY a3 "&a2;&a2;&a2;&a2;&a2;&a2;&a2;&a2;&a2;&a2;">
              <!ENTITY a4 "&a3;&a3;&a3;&a3;&a3;&a3;&a3;&a3;&a3;&a3;">
              <!ENTITY a5 "&a4;&a4;&a4;&a4;&a4;&a4;&a4;&a4;&a4;&a4;">
              <!ENTITY a6 "&a5;&a5;&a5;&a5;&a5;&a5;&a5;&a5;&a5;&a5;">
              <!ENTITY a7 "&a6;&a6;&a6;&a6;&a6;&a6;&a6;&a6;&a6;&a6;">
              <!ENTITY a8 "&a7;&a7;&a7;&a7;&a7;&a7;&a7;&a7;&a7;&a7;">
              <!ENTITY a9 "&a8;&a8;&a8;&a8;&a8;&a8;&a8;&a8;&a8;&a8;">
            ]>
            <root>&a9;</root>""";

        File file = getTestFile(maliciousXml);

        assertThatThrownBy(() -> XpathUtils.evaluate(file, "/root"))
            .isExactlyInstanceOf(XPathExpressionException.class)
            .hasCauseExactlyInstanceOf(SAXParseException.class)
            .hasMessageContaining("JAXP00010001: The parser has encountered more than \"")
            .hasMessageContaining("\" entity expansions in this document; this is the limit imposed by \"jdk.xml.entityExpansionLimit\".");
    }

    @Test
    void shouldNotProcessXIncludeByDefault() throws Exception {
        Path secret = Files.createTempFile("secret", ".txt");
        Files.writeString(secret, "Gangadhar is Shaktimaan");

        String maliciousXml = """
            <?xml version="1.0"?>
               <root xmlns:xi="http://www.w3.org/2001/XInclude">
                 <son>
                   <grandson>
                     <xi:include href="%s" parse="text"/>
                   </grandson>
                 </son>
               </root>""".formatted(secret.toUri());

        File file = getTestFile(maliciousXml);

        String result = XpathUtils.evaluate(file, "/root/son/grandson");
        assertThat(result).isEmpty();
    }

    @Test
    public void shouldReturnEmptyStringWhenMatchedNodeIsNotTextNode() throws Exception {
        String xpath = "/root/son";
        String value = XpathUtils.evaluate(getTestFile(), xpath);
        assertThat(value).isEmpty();
    }

    @Test
    public void shouldParseUTFFilesWithBOM() throws Exception {
        String xpath = "//son/grandson[@name=\"anyone\"]/@address";
        boolean exists = XpathUtils.nodeExists(getTestFileUsingUTFWithBOM(), xpath);

        assertThat(exists).isTrue();
    }

    private File getTestFileUsingUTFWithBOM() throws IOException {
        testFile = File.createTempFile("xpath", null, temporaryFolder);
        saveUtfFileWithBOM(testFile, XML);

        return testFile;
    }

    public static void saveUtfFileWithBOM(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos, UTF_8))) {
            // write UTF8 BOM mark if file is empty
            if (file.length() < 1) {
                final byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
                fos.write(bom);
            }

            if (content != null) {
                bw.write(content);
            }
        }
    }

    @Test
    public void shouldEvaluateXpathOfCustomer() throws Exception {
        String xpath = "//coverageReport2/project/@coverage";
        File file = new File("src/test/resources/data/customer/CoverageSummary.xml");
        InputSource inputSource = new InputSource(file.getPath());
        assertThat(XpathUtils.nodeExists(inputSource, xpath)).isTrue();
        String value = XpathUtils.evaluate(file, xpath);
        assertThat(value).isEqualTo("27.7730732");
    }

    private File getTestFile() throws IOException {
        return getTestFile(XML);
    }

    private File getTestFile(String xml) throws IOException {
        testFile = File.createTempFile("xpath", null, temporaryFolder);
        Files.writeString(testFile.toPath(), xml, UTF_8);
        return testFile;
    }
}



