#!bin/bash


echo "start"
echo ""

myip=$(hostname --ip-address)
echo "$myip"

input="/home/ubuntu/xacmlProject/baseline/config/ips"

me=0
while read line; do    
    if [[ $line == $myip ]]
    then
        echo "yes me is $me"
    fi
    ((me++))
done < $input


bash runscripts/myrun.sh org.example.baselineServer $me 10000 true