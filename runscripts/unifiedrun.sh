#!bin/bash
echo "unified running starts..."

echo "downloading library..."
cd /home/ubuntu/xacmlProject
rm -rf baseline
git clone https://github.com/Koll-Stone/baseline.git

echo "setting environment..."
cd /home/ubuntu/xacmlProject/baseline
mvn install:install-file -Dfile=lib/BFT-SMaRt.jar -DgroupId=org.ulisboa -DartifactId=bftsmart -Dpackaging=jar -Dversion=1.0
mvn dependency:build-classpath -Dmdep.outputFile=./runscripts/cpcontent
echo ":$(pwd)/target/classes" >> ./runscripts/cpcontent

echo "running code..."
myip=$(hostname --ip-address)
echo "$myip"
input="/home/ubuntu/xacmlProject/baseline/config/ips"

ind=0
me=-1
flag=-1
while read line; do    
    if [[ $line == $myip ]]
    then
        echo "yes me is $ind"
        flag=1
        me=ind
    fi
    ((ind++))
done < $input



if [ $flag -eq 1 ]
then
    bash runscripts/myrun.sh org.example.baselineServer $me 10000 true
else
    bash runscripts/myrun.sh org.example.baselineClient 1 100 100 0 true
fi

