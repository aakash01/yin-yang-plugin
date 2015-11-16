# Yang to Yin Converter Maven Plugin

This is a maven plugin which converts yang files to yin using pyang tool. 

#Prerequisite 

Make sure that pyang is installed and added to path. To check if pyang is installed or not , run pyang -v  on command prompt. 


# Usage 
Sample pom file 

```xml
            <plugin>
                <groupId>com.anuta.internal</groupId>
                <artifactId>yin-yang-plugin</artifactId>
                <version>1.0</version>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.maven</groupId>
                        <artifactId>maven-plugin-api</artifactId>
                        <version>2.0</version>
                    </dependency>
                </dependencies>
                <executions>
                    <execution>
                        <phase>compile</phase>
                        <goals>
                            <goal>convert</goal>
                        </goals>
                        <configuration>
                            <directories>
                                <param>${project.basedir}/src/main/resources</param>
                            </directories>
                            <skipConversion>${project.yang.skipConversion}</skipConversion>
                            <failOnError>false</failOnError>
                            <formatArgs>
                                <param>--keep-comments</param>
                            </formatArgs>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
			
```
