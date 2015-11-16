package com.anuta.internal;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.swing.text.BadLocationException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResource;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResourceCollection;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.WriterFactory;

/**
 * Created by Aakash on 10/12/2015.
 * @author aakash01.nitb@gmail.com
 */

/**
 * Echos an object string to the output screen.
 *
 * @goal convert
 * @requiresProject false
 */
public class YangConverter extends AbstractMojo {

   private static final String CACHE_PROPERTIES_FILENAME = "yin-yang-cache.properties";
   private static final String[] DEFAULT_INCLUDES = new String[] { "**/*.yang" };


   /**
    * Project's target directory as specified in the POM.
    *
    * @parameter expression="${project.build.directory}"
    * @readonly
    * @required
    */
   private File targetDirectory;
   /**
    * Project's base directory.
    *
    * @parameter expression="${basedir}"
    * @readonly
    * @required
    */
   private File basedir;

   /**
    * Location of the Yang source files to convert.
    *
    * @parameter
    */
   private File[] directories;

   /**
    * List of fileset patterns for Yang source locations to include in conversion.
    * Patterns are relative to the project source and test source directories.
    * When not specified, the default include is <code>**&#47;*.yang</code>
    *
    * @parameter
    */
   private String[] includes;

   /**
    * List of fileset patterns for Yang source locations to exclude from conversion.
    * Patterns are relative to the project source and test source directories.
    * When not specified, there is no default exclude.
    *
    * @parameter
    */
   private String[] excludes;

   /**
    * The file encoding used to read and write source files.
    * When not specified and sourceEncoding also not set,
    * default is platform file encoding.
    *
    * @parameter default-value="${project.build.sourceEncoding}"
    */
   private String encoding;

   /**
    * Whether the conversion is skipped.
    *
    * @parameter default-value="false" expression="${skipConvert}"
    */
   private Boolean skipConversion;

   /**
    * list of custom arguments which can be passed while converting yang to yin
    * Eg: --yin-pretty-strings
    *
    * @parameter
    */
   private String[] convertArgs;

   /**
    * Whether the yang format is skipped.
    *
    * @parameter default-value="false" expression="${skipFormat}"
    */
   private Boolean skipFormat;

   /**
    * list of custom arguments which can be passed while converting yang to yin
    * Eg: --keep-comments
    *
    * @parameter
    */
   private String[] formatArgs;
   

   private PlexusIoFileResourceCollection collection;

   private static int count = 0;

   /**
    * Whether the error is skipped.
    *
    * @parameter default-value="true" expression="${failOnError}"
    */
   private Boolean failOnError=true;

   @Override
   public void execute() throws MojoExecutionException, MojoFailureException {

      if(!isPyangInstalled()){
         getLog().error("Pyang is not installed. Skip conversion.");
         return;
      }

      long startClock = System.currentTimeMillis();

      createResourceCollection();

      List files = new ArrayList();
      try {

         if (directories != null) {
            for (File directory : directories) {
               if (directory.exists() && directory.isDirectory()) {
                  collection.setBaseDir(directory);
                  addCollectionFiles(files);
               }
            }
         }
      } catch (IOException e) {
         throw new MojoExecutionException("Unable to find files using includes/excludes", e);
      }

      int numberOfFiles = files.size();
      Log log = getLog();

      if (numberOfFiles > 0) {

         ResultCollector formatRC = new ResultCollector();
         ResultCollector convertRC = new ResultCollector();
         Properties hashCache = readFileHashCacheFile();

         String basedirPath = getBasedirPath();
         for (int i = 0, n = files.size(); i < n; i++) {
            File file = (File) files.get(i);
            convertAndFormatFile(file, formatRC, convertRC, hashCache, basedirPath);
         }

         storeFileHashCache(hashCache);

         long endClock = System.currentTimeMillis();

         if(!skipFormat) {
            log.info("Number of yang/yin files to format: " + numberOfFiles);
            log.info("Successfully formatted: " + formatRC.successCount + " file(s)");
            log.info("Fail to format        : " + formatRC.failCount + " file(s)");
            log.info("Skipped               : " + formatRC.skippedCount + " file(s)");
         }
         
         if(!skipConversion) {
            log.info("Number of yang/yin files to convert: " + numberOfFiles);
            log.info("Successfully Converted: " + convertRC.successCount + " file(s)");
            log.info("Fail to convert        : " + convertRC.failCount + " file(s)");
            log.info("Skipped               : " + convertRC.skippedCount + " file(s)");
         }
         log.info("Approximate time taken: " + ((endClock - startClock) / 1000) + "s");
      }

   }

   /**
    * This method checks if a pyang is installed or not. If pyang is not installed it will silently return. 
    * @return
    */
   private boolean isPyangInstalled(){
      try {
         ProcessBuilder pb = new ProcessBuilder(getCommandString(OperationType.VERSION));
         Process pr = pb.start();
         int exitVal = pr.waitFor();
         if(exitVal == 0){
            BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            getLog().info("using " + input.readLine());
            return true;
         } else {
            return false;
         }
      } catch (IOException e) {
         e.printStackTrace();
         return false;
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
      return false;
   }

   /**
    * Create a {@link PlexusIoFileResourceCollection} instance to be used by this mojo.
    * This collection uses the includes and excludes to find the source files.
    */
   void createResourceCollection() {
      collection = new PlexusIoFileResourceCollection();
      if (includes != null && includes.length > 0) {
         collection.setIncludes(includes);
      } else {
         collection.setIncludes(DEFAULT_INCLUDES);
      }
      collection.setExcludes(excludes);
      collection.setIncludingEmptyDirectories(false);

      IncludeExcludeFileSelector fileSelector = new IncludeExcludeFileSelector();
      fileSelector.setIncludes(DEFAULT_INCLUDES);
      collection.setFileSelectors(new FileSelector[] { fileSelector });

   }

   /**
    * Add source files from the {@link PlexusIoFileResourceCollection} to the files list.
    *
    * @param files
    * @throws IOException
    */
   void addCollectionFiles(List files) throws IOException {
      Iterator resources = collection.getResources();
      while (resources.hasNext()) {
         PlexusIoFileResource resource = (PlexusIoFileResource) resources.next();
         files.add(resource.getFile());
      }
   }

   private void storeFileHashCache(Properties props) {
      File cacheFile = new File(targetDirectory, CACHE_PROPERTIES_FILENAME);
      try {
         OutputStream out = new BufferedOutputStream(new FileOutputStream(cacheFile));
         props.store(out, null);
      } catch (FileNotFoundException e) {
         getLog().warn("Cannot store file hash cache properties file", e);
      } catch (IOException e) {
         getLog().warn("Cannot store file hash cache properties file", e);
      }
   }

   private Properties readFileHashCacheFile() {
      Properties props = new Properties();
      Log log = getLog();
      if (!targetDirectory.exists()) {
         targetDirectory.mkdirs();
      } else if (!targetDirectory.isDirectory()) {
         log.warn("Something strange here as the " + "supposedly target directory is not a directory.");
         return props;
      }

      File cacheFile = new File(targetDirectory, CACHE_PROPERTIES_FILENAME);
      if (!cacheFile.exists()) {
         return props;
      }

      try {
         props.load(new BufferedInputStream(new FileInputStream(cacheFile)));
      } catch (FileNotFoundException e) {
         log.warn("Cannot load file hash cache properties file", e);
      } catch (IOException e) {
         log.warn("Cannot load file hash cache properties file", e);
      }
      return props;
   }

   private String getBasedirPath() {
      try {
         return basedir.getCanonicalPath();
      } catch (Exception e) {
         return "";
      }
   }

   /**
    * @param file
    * @param convertRC
    * @param hashCache
    * @param basedirPath
    */
   private void convertAndFormatFile(File file, ResultCollector formatRC, ResultCollector convertRC, Properties hashCache, String basedirPath) {
      try {
         doConvertAndFormatFile(file,formatRC,  convertRC, hashCache, basedirPath);
      } catch (IOException e) {
         formatRC.failCount++;
         convertRC.failCount++;
         getLog().warn(e);
      } catch (BadLocationException e) {
         formatRC.failCount++;
         convertRC.failCount++;
         getLog().warn(e);
      }
   }

   /**
    * @param str
    * @return
    * @throws UnsupportedEncodingException
    */
   private String md5hash(String str) throws UnsupportedEncodingException {
      return DigestUtils.md5Hex(str.getBytes(encoding));
   }

   /**
    * 
    * @param file
    * @param formatRC
    * @param convertRC
    * @param hashCache
    * @param basedirPath
    * @throws IOException
    * @throws BadLocationException
    */
   private void doConvertAndFormatFile(File file,ResultCollector formatRC, ResultCollector convertRC, Properties hashCache, String basedirPath)
                  throws IOException, BadLocationException {
      Log log = getLog();
      log.debug("Processing file: " + file);

      String code = readFileAsString(file);
      String originalHash = md5hash(code);

      String canonicalPath = file.getCanonicalPath();
      String path = canonicalPath.substring(basedirPath.length());
      String cachedHash = hashCache.getProperty(path);
      if (cachedHash != null && cachedHash.equals(originalHash)) {
         convertRC.skippedCount++;
         formatRC.skippedCount++;
         return;
      }

      convertAndFormat(file, formatRC, convertRC, hashCache, log, originalHash, path);

   }

   /**
    * 
    * @param file
    * @param formatRC
    * @param convertRC
    * @param hashCache
    * @param log
    * @param originalHash
    * @param path
    * @return
    * @throws IOException
    */
   private boolean convertAndFormat(File file, ResultCollector formatRC, ResultCollector convertRC,
                  Properties hashCache, Log log, String originalHash, String path) throws IOException {
      Runtime rt = Runtime.getRuntime();

      if(!skipFormat){
         getLog().info("Formatting file "+file.getName());
         if(formatYang(file, formatRC, log, rt)){
            getLog().info("Successfully formatted "+file.getName());
            formatRC.successCount++;
         }
      } else {
         getLog().info("Format is skipped");
      }
      if(!skipConversion){
         getLog().info("Converting file "+file.getName());
         if(convertYangToYin(file, convertRC, log, rt)){
            getLog().info("Successfully converted "+file.getName());
            convertRC.successCount++;
         }
      } else {
         getLog().info("Conversion is skipped");
      }

      hashCache.setProperty(path, originalHash);
      return true;
   }

   private boolean isWindows(){
      String osName = System.getProperty("os.name");
      return osName.startsWith("Windows");
   }
   /**
    * get the command string. 
    * Based on the ostype this can vary. right now supported only for windows. 
    * @param operation
    * @return
    */
   private List<String> getCommandString(OperationType operation){
      List<String> commandBuilder = new ArrayList<String>();
      if(isWindows()){
         commandBuilder.addAll(Arrays.asList("cmd", "/c"));
      }
      commandBuilder.add("pyang");
      if(operation == OperationType.VERSION){
         commandBuilder.add("-v");
      } else if(operation == OperationType.FORMAT){
         if(formatArgs != null && formatArgs.length > 0){
            for(String arg : formatArgs){
               commandBuilder.add(arg);
            }
         }
         commandBuilder.add("-f");
         commandBuilder.add("yang");
      } else if(operation == OperationType.CONVERT){
         if(convertArgs != null && convertArgs.length > 0){
            for(String arg : convertArgs){
               commandBuilder.add(arg);
            }
         }
         commandBuilder.add("-f");
         commandBuilder.add("yin");
      }
      return commandBuilder;
   }

   private boolean formatYang(File file, ResultCollector rc, Log log, Runtime rt)
                  throws IOException {
      StringBuilder result = new StringBuilder();
      try {
         List<String> commandString = getCommandString(OperationType.FORMAT);
         commandString.add(file.getCanonicalPath());
         ProcessBuilder pb = new ProcessBuilder(commandString);
         pb.directory(file.getParentFile());
         getLog().debug("Executing command " + commandString.toString());
         Process pr = pb.start();
         BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
         String line=null;

         while((line=input.readLine()) != null) {
            result.append(line);
            result.append("\n");
         }
         int exitVal = pr.waitFor();
         if(exitVal != 0) {
            BufferedReader error = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
            StringBuilder errorBuilder = new StringBuilder();
            String err=null;

            while((err=error.readLine()) != null) {
               errorBuilder.append(err);
               errorBuilder.append("\n");
            }
            log.error(errorBuilder.toString());
            if(failOnError){
               throw new RuntimeException(errorBuilder.toString());
            } else{
               rc.failCount++;
               return false;
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      } catch (InterruptedException e) {
         e.printStackTrace();
      }

      if (result == null) {
         rc.failCount++;
         return false;
      }
      String convertedCode = result.toString();
      writeStringToFile(convertedCode, file);
      return true;
      
   }

   private boolean convertYangToYin(File file, ResultCollector rc, Log log, Runtime rt)
                  throws IOException {
      StringBuilder result = new StringBuilder();
      try {
         List<String> commandString = getCommandString(OperationType.CONVERT);
         commandString.add(file.getCanonicalPath());
         ProcessBuilder pb = new ProcessBuilder(commandString);
         pb.directory(file.getParentFile());
         Process pr = pb.start();
         BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
         String line=null;

         while((line=input.readLine()) != null) {
            result.append(line);
            result.append("\n");
         }
         int exitVal = pr.waitFor();
         if(exitVal != 0) {
            BufferedReader error = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
            StringBuilder errorBuilder = new StringBuilder();
            String err=null;

            while((err=error.readLine()) != null) {
               errorBuilder.append(err);
               errorBuilder.append("\n");
            }
            log.error(errorBuilder.toString());
            if(failOnError){
               throw new RuntimeException(errorBuilder.toString());
            } else{
               rc.failCount++;
               return false;
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      } catch (InterruptedException e) {
         e.printStackTrace();
      }

      if (result == null) {
          rc.failCount++;
         return false;
      }
      String convertedCode = result.toString();
      File yinFile = new File(getYinFileName(file));
      if(!yinFile.exists()){
         yinFile.createNewFile();
      }
      writeStringToFile(convertedCode, yinFile);
      return true;
   }

   private String getYinFileName(File yangFile){
      File parentDir = yangFile.getParentFile();
      String fileName = yangFile.getName().substring(0, yangFile.getName().indexOf("."));
      return parentDir+"/"+fileName+".yin";
   }

   /**
    * Read the given file and return the content as a string.
    *
    * @param file
    * @return
    * @throws java.io.IOException
    */
   private String readFileAsString(File file) throws java.io.IOException {
      StringBuilder fileData = new StringBuilder(1000);
      BufferedReader reader = null;
      try {
         reader = new BufferedReader(ReaderFactory.newReader(file, encoding));
         char[] buf = new char[1024];
         int numRead = 0;
         while ((numRead = reader.read(buf)) != -1) {
            String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
            buf = new char[1024];
         }
      } finally {
         IOUtil.close(reader);
      }
      return fileData.toString();
   }

   /**
    * Write the given string to a file.
    *
    * @param str
    * @param file
    * @throws IOException
    */
   private void writeStringToFile(String str, File file) throws IOException {
      if (!file.exists() && file.isDirectory()) {
         return;
      }

      BufferedWriter bw = null;
      try {
         bw = new BufferedWriter(WriterFactory.newWriter(file, encoding));
         bw.write(str);
      } finally {
         IOUtil.close(bw);
      }
   }

   private class ResultCollector {
      private int successCount;
      private int failCount;
      private int skippedCount;
   }
   
   private enum OperationType {
      VERSION,
      CONVERT,
      FORMAT
   }
   
   public static void main(String args[]){
      YangConverter yangConverter = new YangConverter();
      System.out.println("testing pyang ");
      System.out.print(yangConverter.isPyangInstalled());
      Runtime rt = Runtime.getRuntime();
      
      File file = new File("D:\\office\\release_4.7\\modelbase\\src\\main\\resources\\anuta\\devicemodel1.yang");
      System.out.println(yangConverter.getYinFileName(file));
      try {
         System.out.println(file.getCanonicalPath());
         System.out.print(yangConverter.isWindows());
         System.getProperties().list(System.out);
      } catch (IOException e) {
         e.printStackTrace();
      }
      System.out.println(file.getName());
      /*try {
         System.out.println(file.getCanonicalPath());
         Process pr = rt.exec("cmd /c pyang44 -f yin "+file.getCanonicalPath());
         BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));
         StringBuilder result = new StringBuilder();
         String line=null;

         while((line=input.readLine()) != null) {
            result.append(line);
            result.append("\n");
         }
         System.out.println(result.toString());
         int exitVal = pr.waitFor();
         if(exitVal != 0) {
            BufferedReader error = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
            StringBuilder errorBuilder = new StringBuilder();
            String err=null;

            while((err=error.readLine()) != null) {
               errorBuilder.append(err);
               errorBuilder.append("\n");
            }
            System.out.println(errorBuilder.toString());
         }
      } catch (IOException e) {
         e.printStackTrace();
      } catch (InterruptedException e) {
         e.printStackTrace();
      }*/
   }
}
