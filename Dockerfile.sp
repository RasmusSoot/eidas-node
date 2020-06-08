FROM maven:3.6.3-jdk-8 as build
COPY . /usr/src/app
WORKDIR /usr/src/app/EIDAS-Parent
RUN mvn clean install -DskipTests -P NodeOnly,DemoToolsOnly -PnodeJcacheIgnite,specificCommunicationJcacheIgnite

FROM tomcat:8.5.53-jdk8-openjdk
COPY --from=build /usr/src/app/EIDAS-SP/target/SP.war /usr/local/tomcat/webapps/SP.war
EXPOSE 8083
CMD ["catalina.sh", "run"]