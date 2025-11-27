$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
Write-Host "Starting LiveAlerts application..."
java -jar target/LiveAlerts-1.0-SNAPSHOT.jar


// ---------------- AFTER REFRESH ----------------
$env:JAVA_HOME="C:\Program Files\Zulu\zulu-21"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn -version


mvn clean package


java -jar target/LiveAlerts-1.0-SNAPSHOT.jar
