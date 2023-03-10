echo "######start..."

echo "######downloading library..."
cd /home/ubuntu/xacmlProject/storage
git pull
cp original/BFT-SMaRt.jar /home/ubuntu/xacmlProject/baseline/lib/

# rm -rf baseline
# git clone https://github.com/Koll-Stone/baseline.git

echo "######setting environment..."
cd /home/ubuntu/xacmlProject/baseline
mvn clean install:install-file -Dfile=lib/BFT-SMaRt.jar -DgroupId=org.ulisboa -DartifactId=bftsmart -Dpackaging=jar -Dversion=1.0
mvn dependency:build-classpath -Dmdep.outputFile=./runscripts/cpcontent
echo ":$(pwd)/target/classes" >> ./runscripts/cpcontent
mvn package

echo "finished"
