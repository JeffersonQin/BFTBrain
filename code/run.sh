java -Xmx55g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=dump.hprof --enable-preview -cp target/classes:target/dependency/* com.gbft.framework.coordination.$@
