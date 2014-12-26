FROM ubuntu:precise

MAINTAINER Michael de Jong "https://github.com/michaeldejong"

RUN apt-get update
RUN apt-get -y upgrade

RUN apt-get -y install python-software-properties
RUN add-apt-repository ppa:webupd8team/java
RUN add-apt-repository ppa:natecarlson/maven3
RUN apt-get update && apt-get -y upgrade

# Automatically accept oracle license
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections

# Install java 8 oracle jdk
RUN apt-get -y install oracle-java8-installer && apt-get clean
RUN update-alternatives --display java 
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# Install git and maven3
RUN apt-get -y install git
RUN apt-get -y install maven3
RUN ln -s /usr/share/maven3/bin/mvn /usr/bin/mvn

RUN apt-get -y install psmisc xfonts-100dpi xfonts-75dpi xfonts-scalable xfonts-cyrillic xvfb libxrender-dev libxtst6 libxi6
RUN apt-get -y install firefox

RUN echo '#!/bin/bash' >> /bin/with-xvfb
RUN echo 'git clone https://github.com/devhub-tud/Java-Gitolite-Manager.git' >> /bin/with-xvfb
RUN echo 'cd Java-Gitolite-Manager' >> /bin/with-xvfb
RUN echo 'mvn -B install -DskipTests=true -Dmaven.javadoc.skip=true' >> /bin/with-xvfb
RUN echo 'cd ../' >> /bin/with-xvfb
RUN echo 'git clone https://github.com/devhub-tud/git-server.git' >> /bin/with-xvfb
RUN echo 'cd git-server' >> /bin/with-xvfb
RUN echo 'mvn -B install -DskipTests=true -Dmaven.javadoc.skip=true' >> /bin/with-xvfb
RUN echo 'cd ../' >> /bin/with-xvfb
RUN echo 'git clone https://github.com/devhub-tud/build-server.git' >> /bin/with-xvfb
RUN echo 'cd build-server' >> /bin/with-xvfb
RUN echo 'mvn -B install -DskipTests=true -Dmaven.javadoc.skip=true' >> /bin/with-xvfb
RUN echo 'cd ../' >> /bin/with-xvfb
RUN echo '/usr/bin/Xvfb :99 -ac -screen 0 1024x768x8 2>/dev/null &' >> /bin/with-xvfb
RUN echo 'sleep 1' >> /bin/with-xvfb
RUN echo '$@' >> /bin/with-xvfb
RUN echo 'exit_value=$?' >> /bin/with-xvfb
RUN echo 'killall Xvfb' >> /bin/with-xvfb
RUN echo 'exit $exit_value' >> /bin/with-xvfb
RUN chmod +x /bin/with-xvfb

ENV DISPLAY :99

