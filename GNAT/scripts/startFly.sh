JAVA="java -XX:ThreadStackSize=256k -XX:+UseCompressedOops -XX:+UseParallelGC"
CP="lib/gnat.jar"
DICT="dictionaries"

#d.mel

nohup ${JAVA} -cp ${CP} -Xmx1500M gnat.server.dictionary.DictionaryServer 56008 ${DICT}/7227/ &
