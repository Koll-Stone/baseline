gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") org.example.baselineServer 0 10000 true &

gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") org.example.baselineServer 1 10000 true &

gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") org.example.baselineServer 2 10000 true &

gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") org.example.baselineServer 3 10000 true &


