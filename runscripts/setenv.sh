echo "######start..."

echo "######downloading library..."
cd /home/ubuntu/xacmlProject
rm -rf baseline
git clone https://github.com/Koll-Stone/baseline.git

echo "######setting environment..."
cd /home/ubuntu/xacmlProject/baseline
mvn install:install-file -Dfile=lib/BFT-SMaRt.jar -DgroupId=org.ulisboa -DartifactId=bftsmart -Dpackaging=jar -Dversion=1.0
mvn dependency:build-classpath -Dmdep.outputFile=./runscripts/cpcontent
echo ":$(pwd)/target/classes" >> ./runscripts/cpcontent


echo "finished"
