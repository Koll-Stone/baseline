#!bin/bash
echo "######unified running starts..."

cd /home/ubuntu/xacmlProject/baseline

echo "######running code..."
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
        me=$ind
    fi
    ((ind++))
done < $input



if [ $flag -eq 1 ]
then
    if [ $me -le 3 ]
    then
        echo "bash runscripts/myrun.sh org.example.baselineServer $me 10000 true true"
        bash runscripts/myrun.sh org.example.baselineServer $me 10000 true true
    else
        client=$(($me-4))
        start=$(($(($client*600))+1001))
        echo "bash runscripts/myrun.sh org.example.baselineClient $start 400 100 10 true true"
        bash runscripts/myrun.sh org.example.baselineClient $start 20 1201 10 true true
    fi   
fi



