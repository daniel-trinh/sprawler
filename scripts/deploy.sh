# For Ubuntu 12.04

# Update repos
sudo apt-get update

# Install git
sudo apt-get install git -y

# Fix dependencies
sudo apt-get -f install -y

# Install curl
sudo apt-get install curl -y

# Install Open JDK 7
sudo apt-get install openjdk-7-jdk -y

# Install sbt
sudo wget http://repo.scala-sbt.org/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.13.0/sbt.deb
sudo dpkg -i sbt.deb 

# Install latest node.js
sudo apt-get install -y software-properties-common python g++ make -y
sudo add-apt-repository -y ppa:chris-lea/node.js
sudo apt-get update
sudo apt-get install nodejs -y


# MANUAL STEP: GENERATE SSH KEYS

cd ~/
mkdir src
cd src
git clone git@github.com:daniel-trinh/sprawler.git
cd sprawler
