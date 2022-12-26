gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") bftsmart.demo.microbenchmarks.ThroughputLatencyServer 0 2000 0 0 false nosig rwd &

gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") bftsmart.demo.microbenchmarks.ThroughputLatencyServer 1 2000 0 0 false nosig rwd &

gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") bftsmart.demo.microbenchmarks.ThroughputLatencyServer 2 2000 0 0 false nosig rwd &

gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") bftsmart.demo.microbenchmarks.ThroughputLatencyServer 3 2000 0 0 false nosig rwd &


