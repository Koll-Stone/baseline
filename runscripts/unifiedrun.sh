#!bin/bash
echo "######unified running starts..."



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
    echo "bash runscripts/myrun.sh org.example.baselineServer $me 10000 true"
    bash runscripts/myrun.sh org.example.baselineServer $me 10000 true
else
    echo "bash runscripts/myrun.sh org.example.baselineClient 1 10 100 0 true"
    bash runscripts/myrun.sh org.example.baselineClient 1 10 100 0 true
fi

