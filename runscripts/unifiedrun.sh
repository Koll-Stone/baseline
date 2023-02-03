#!bin/bash
echo "unified running starts..."

myip=$(hostname --ip-address)
echo "$myip"
input="/home/ubuntu/xacmlProject/baseline/config/ips"

me=0
flag=-1
while read line; do    
    if [[ $line == $myip ]]
    then
        echo "yes me is $me"
        flag=1
    fi
    ((me++))
done < $input



if [ $flag -eq 1 ]
then
    echo "bash runscripts/myrun.sh org.example.baselineServer $me 10000 true"
else
    echo "bash runscripts/myrun.sh org.example.baselineClient 1 100 100 0 true"
fi

