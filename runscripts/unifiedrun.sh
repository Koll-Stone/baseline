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

servernum=7

if [ $flag -eq 1 ]
then
    if [ $me -le $(($servernum-1)) ]
    then
        command="runscripts/myrun.sh org.example.baselineServer $me 10000 true false"
        echo $command
        bash $command
    else
        client=$(($me-$servernum))
        start=$(($(($client*1000))+1001))
        command="runscripts/myrun.sh org.example.baselineClient $start 150 401 10 true false"
        echo $command
        bash $command
    fi   
fi



