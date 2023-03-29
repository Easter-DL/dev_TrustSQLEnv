
Code status:
------------

! dev_TrustSQLEnv is a source code package, download dev_TrustSQLEnv, if you don't need source code.  

## TrustSQLEnv (TrustSQL Environment)

TrustSQLEnv is an envrionment for deploy and use Relational Distriuted Ledgers.  
It consists of TOSS(Trusted Ordering-Stamping Server) , DNOM (Distributed Node Manager) and Demo Applications.  

## TOSS (Trusted Ordering-Stamping Server) 
TOSS is single point of receipt for all user tranactions.  
It works as a total ordering broadcaster and also message server.  
Trusted means it includes singnature stamp of trusted authorigy.  

## DNOM (Distributed Node Manager)  
DNOM is an agent to subscribe messages from TOSS and insert it into TrustSQL.  

## Demo Applications.  
You can find how it works and what can you do with it.  


Build:
--------

#### build path.
If you download dev_TrustSQLEnv at @HOME/dev_TrustSQLEnv  
export RDLMSPATH=$HOME/dev_TrustSQLEnv  
export RDLMSBIN=$RDLMSPATH/bin  
export PATH=$PATH:$RDLMSBIN  

#### compile
./build.sh  


#### deploy.
./deploy.sh  


Run:  
--------

1. DNOM  
dnom [config_file]

2. TOSS
toss [config_file]

3. Wallet
wallet


Who we are:
----------
EasterC&I inc, is a distributed ledger technology company in South, Korea.
 

Help:
-----
If you need any help please send me an e-mail.
dwpark@easter.co.kr


License:
--------

***************************************************************************

NOTE: 

TrustSQL is specifically available only under version 2 of the GNU
General Public License (GPLv2). (I.e. Without the "any later version"
clause.) This is inherited from MariaDB.

***************************************************************************
