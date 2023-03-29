#!/bin/bash

echo -e "#############################################################"
echo -e "#   Copyright © 2021.11 TrustDB ( www.trust-db.com )        #"
echo -e "#	All rights reserved                                  #"
echo -e "#                                                           #"
echo -e "#   Scripts for making nwork directories for RDLMS          #"
echo -e "#                                                           #"
echo -e "#   Create Date : 2021/11/14                                #"
echo -e "#############################################################"
echo -e ""


echo -e ""
echo -e "Where is your work_home for RDLMS " 
read target

if [ -d ${target} ]; then 
echo -e ${target} "is already used."
exit
fi

export RDLMS_HOME=${target}

mkdir ${target}
mkdir ${target}/dnom
mkdir ${target}/toss
mkdir ${target}/demo

cp ./res/log4j.properties ./bin
cp ./res/dnom_config_template.json ./${target}/dnom
cp ./res/dnom_start.sh ./bin/
cp ./res/toss_config_template.json ./${target}/toss
cp ./res/toss_start.sh ./bin/
cp ./res/demo.sh ./${target}/demo
cp ./res/WalletManager.sh ./bin/
