# Copyright ${copyrightYear} ThoughtWorks, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

###############################################################################################
# This file is autogenerated by the repository at https://github.com/gocd/gocd.
# Please file any issues or PRs at https://github.com/gocd/gocd
###############################################################################################

FROM alpine:latest as gocd-server-unzip
ARG UID=1000
<#if useFromArtifact >
COPY go-server-${fullVersion}.zip /tmp/go-server-${fullVersion}.zip
<#else>
RUN \
  apk --no-cache upgrade && \
  apk add --no-cache curl && \
  curl --fail --location --silent --show-error "https://download.gocd.org/binaries/${fullVersion}/generic/go-server-${fullVersion}.zip" > /tmp/go-server-${fullVersion}.zip
</#if>
RUN unzip /tmp/go-server-${fullVersion}.zip -d /
RUN mkdir -p /go-server/wrapper /go-server/bin && \
    mv /go-server-${goVersion}/LICENSE /go-server/LICENSE && \
    mv /go-server-${goVersion}/bin/go-server /go-server/bin/go-server && \
    mv /go-server-${goVersion}/lib /go-server/lib && \
    mv /go-server-${goVersion}/logs /go-server/logs && \
    mv /go-server-${goVersion}/run /go-server/run && \
    mv /go-server-${goVersion}/wrapper-config /go-server/wrapper-config && \
    mv /go-server-${goVersion}/wrapper/wrapper-linux* /go-server/wrapper/ && \
    mv /go-server-${goVersion}/wrapper/libwrapper-linux* /go-server/wrapper/ && \
    mv /go-server-${goVersion}/wrapper/wrapper.jar /go-server/wrapper/ && \
    chown -R ${r"${UID}"}:0 /go-server && chmod -R g=u /go-server

FROM ${distro.getBaseImageLocation(distroVersion)}

LABEL gocd.version="${goVersion}" \
  description="GoCD server based on ${distro.getBaseImageLocation(distroVersion)}" \
  maintainer="ThoughtWorks, Inc. <support@thoughtworks.com>" \
  url="https://www.gocd.org" \
  gocd.full.version="${fullVersion}" \
  gocd.git.sha="${gitRevision}"

# the ports that go server runs on
EXPOSE 8153

<#list additionalFiles as filePath, fileDescriptor>
ADD ${fileDescriptor.url} ${filePath}
</#list>

# force encoding
ENV LANG=en_US.UTF-8 LANGUAGE=en_US:en LC_ALL=en_US.UTF-8
<#list distro.getEnvironmentVariables(distroVersion) as key, value>
ENV ${key}="${value}"
</#list>

ARG UID=1000

RUN \
<#if additionalFiles?size != 0>
# add mode and permissions for files we added above
  <#list additionalFiles as filePath, fileDescriptor>
  chmod ${fileDescriptor.mode} ${filePath} && \
  chown ${fileDescriptor.owner}:${fileDescriptor.group} ${filePath} && \
  </#list>
</#if>
# add our user and group first to make sure their IDs get assigned consistently,
# regardless of whatever dependencies get added
# add user to root group for gocd to work on openshift
<#list distro.createUserAndGroupCommands as command>
  ${command} && \
</#list>
<#list distroVersion.installPrerequisitesCommands as command>
  ${command} && \
</#list>
<#list distro.getInstallPrerequisitesCommands(distroVersion) as command>
  ${command} && \
</#list>
<#list distro.getInstallJavaCommands(project) as command>
  ${command} && \
</#list>
  mkdir -p /go-server /docker-entrypoint.d /go-working-dir /godata

ADD docker-entrypoint.sh /

COPY --from=gocd-server-unzip /go-server /go-server
# ensure that logs are printed to console output
COPY --chown=go:root logback-include.xml /go-server/config/logback-include.xml
COPY --chown=go:root install-gocd-plugins git-clone-config /usr/local/sbin/

RUN chown -R go:root /docker-entrypoint.d /go-working-dir /godata /docker-entrypoint.sh \
    && chmod -R g=u /docker-entrypoint.d /go-working-dir /godata /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]

USER go
