#!/bin/bash

# Determine the directory of the script
SCRIPT_DIR=$(dirname "$(realpath "$0")")

# Default port number
PORT=8787

# Parse command-line options
while getopts "p:" opt; do
  case ${opt} in
    p )
      PORT=$OPTARG
      ;;
    \? )
      echo "Usage: initialize.sh [-p port]"
      exit 1
      ;;
  esac
done

# Screen session name
SCREEN_NAME="vehiclerouting${PORT}"

# Check if the script is running in a screen session
if [ -z "$STY" ]; then
  # If not, check if a screen session with the same name exists and detach it
  if screen -list | grep -q "${SCREEN_NAME}"; then
    screen -S $SCREEN_NAME -X quit
  fi
  # Start a new screen session and run the script in it
  screen -dmS $SCREEN_NAME /bin/bash "$0" "$@"
  exit 0
fi

# Install and configure Maven
TMP_MAVEN_VERSION=3.9.5
wget https://apache.org/dist/maven/maven-3/$TMP_MAVEN_VERSION/binaries/apache-maven-$TMP_MAVEN_VERSION-bin.tar.gz -P /tmp
sudo tar xf /tmp/apache-maven-*.tar.gz -C /opt
sudo rm /tmp/apache-maven-*-bin.tar.gz
sudo ln -s /opt/apache-maven-$TMP_MAVEN_VERSION /opt/maven
sudo touch /etc/profile.d/maven.sh
sudo chown root /etc/profile.d/maven.sh

sudo bash -c 'cat << EOF > /etc/profile.d/maven.sh
export JAVA_HOME=/usr/lib/jvm/default-java
export M2_HOME=/opt/maven
export MAVEN_HOME=/opt/maven
export PATH=/opt/maven/bin:\$PATH
EOF'

sudo chmod +x /etc/profile.d/maven.sh
source /etc/profile.d/maven.sh
mvn -v

# Set JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-amd64

# Change to the project directory
cd "$SCRIPT_DIR/java/vehicle-routing-time-windows/"

# Run Maven command with the specified port
mvn quarkus:dev -Dquarkus.http.port=$PORT -Dquarkus.http.host=0.0.0.0
#mvn package -Dquarkus.http.port=$PORT -Dquarkus.http.host=0.0.0.0 -Dmaven.test.skip
