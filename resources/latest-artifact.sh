# Artifactory location
server=http://artifactory:8081/artifactory
repo=snapshots

# Maven artifact location
name=spring-petclinic
artifact=org/springframework/samples/$name
path=$server/$repo/$artifact
version=`curl -s $path/maven-metadata.xml | grep latest | sed "s/.*<latest>\([^<]*\)<\/latest>.*/\1/"`
build=`curl -s $path/$version/maven-metadata.xml | grep '<value>' | head -1 | sed "s/.*<value>\([^<]*\)<\/value>.*/\1/"`
war=$name-$build.war
url=$path/$version/$war

# Download
echo $url
wget -N -O petclinic.war $url
