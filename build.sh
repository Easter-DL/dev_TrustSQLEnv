#!/bin/bash
export CLASSPATH="./bin:./libs/log4j-api-2.14.1.jar:./log4j-core-2.14.1.jar:./libs/json-simple-1.1.1.jar:./libs/log4j-1.2-api-2.14.1.jar"

rdlms_version="v0.1"

find ./src -name *.java -print > compiletarget.txt
rm -r ./bin/org
javac -d ./bin @compiletarget.txt

cd bin
jar -cf trustsql_rdlms_commnuity_${rdlms_version}.jar org
mv trustsql_rdlms_commnuity_${rdlms_version}.jar ../
cd ..
cp trustsql_rdlms_commnuity_${rdlms_version}.jar ./libs
cp trustsql_rdlms_commnuity_${rdlms_version}.jar ${RDLMSPATH}/libs/