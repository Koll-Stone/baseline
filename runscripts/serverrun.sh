gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") org.example.testserver 0 &

gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") org.example.testserver 1 &

gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") org.example.testserver 2 &

gnome-terminal -- /usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="config/logback.xml" -Dfile.encoding=UTF-8 -classpath $(cat "./runscripts/cpcontent") org.example.testserver 3 &
