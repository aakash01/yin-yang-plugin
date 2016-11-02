/*
 * This computer program is the confidential information and proprietary trade
 * secret of Anuta Networks, Inc. Possessions and use of this program must
 * conform strictly to the license agreement between the user and
 * Anuta Networks, Inc., and receipt or possession does not convey any rights
 * to divulge, reproduce, or allow others to use this program without specific
 * written authorization of Anuta Networks, Inc.
 * 
 * Copyright (c) 2011-2012 Anuta Networks, Inc. All Rights Reserved.
 */
package com.anuta.internal;

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
import java.util.Scanner;
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
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;

/**
 * Created by Aakash on 12/22/2015.
 */
public abstract class YangHelperMojo extends AbstractMojo {

   private static final String[] DEFAULT_INCLUDES = new String[] { "**/*.yang" };
   private static final String[] DEFAULT_EXCLUDES = new String[] { "**/ietf*.yang" };


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
    * Project's source directory as specified in the POM.
    * 
    * @parameter default-value="${basedir}/src"
    * 
    */
   private File sourceDirectory;
   

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
    * recomended pyang version 
    *
    * @parameter default-value="1.6"
    *
    */
   private String pyangVersion ;

   private PlexusIoFileResourceCollection collection;

   private static int count = 0;

   /**
    * Whether the error is skipped.
    *
    * @parameter default-value="true" expression="${failOnError}"
    */
   private Boolean failOnError=true;

   /**
    * YANG_MODPATH
    * 
    * @parameter
    */
   private String yangMODPath;
   
   public void executeGoal(OperationType operationType) throws MojoExecutionException, MojoFailureException {

      Log log = getLog();
      if(null != excludes && excludes.length>0){
         for(String excl : excludes){
            log.info("Excluding file " + excl);
         }
      }

      if(!isPyangInstalled()){
         log.error("Pyang is not installed. Skip conversion.");
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
         } else if (this.sourceDirectory != null && this.sourceDirectory.exists()
                        && this.sourceDirectory.isDirectory()) {
            log.info("Using Source Directory." + this.sourceDirectory);
            collection.setBaseDir(this.sourceDirectory);
            addCollectionFiles(files);
         } else {
            log.error("No source directory specified to scan yang files.");
         }
      } catch (IOException e) {
         throw new MojoExecutionException("Unable to find files using includes/excludes", e);
      }

      int numberOfFiles = files.size();

      log.info("Number of files for "+operationType+" "+numberOfFiles);
      if (numberOfFiles > 0) {

         String basedirPath = getBasedirPath();
            Properties hashCache = readFileHashCacheFile();
            ResultCollector resultCollector = new ResultCollector();
            for (int i = 0, n = files.size(); i < n; i++) {
               File file = (File) files.get(i);
               performOperation(file, resultCollector,  hashCache, basedirPath);
            }
            log.info("\nOperation            : "+operationType);
            log.info("Number of yang files : " + numberOfFiles);
            log.info("Successful           : " + resultCollector.successCount + " file(s)");
            log.info("Failed               : " + resultCollector.failCount + " file(s)");
            log.info("Skipped              : " + resultCollector.skippedCount + " file(s)\n");
            storeFileHashCache(hashCache);
         long endClock = System.currentTimeMillis();

         log.info("Approximate time taken: " + ((endClock - startClock) / 1000) + "s");
      }
   }

   public String getYangMODPath() {
      return yangMODPath;
   }

   private double versionCompare(String version1, String version2) {
      Scanner v1Scanner = new Scanner(version1);
      Scanner v2Scanner = new Scanner(version2);
      v1Scanner.useDelimiter("\\.");
      v2Scanner.useDelimiter("\\.");

      while (v1Scanner.hasNextInt() && v2Scanner.hasNextInt()) {
         int v1 = v1Scanner.nextInt();
         int v2 = v2Scanner.nextInt();
         if (v1 < v2) {
            return -1;
         } else if (v1 > v2) {
            return 1;
         }
      }

      if (v1Scanner.hasNextInt()) {
         return 1;
      } else if (v2Scanner.hasNextInt()) {
         return -1;
      } else {
         return 0;
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
            String pyang_version = input.readLine();
            getLog().info("using " + pyang_version);
            if(pyang_version.contains("pyang ")) {
               String version = pyang_version.substring(pyang_version.indexOf("pyang ") + 6);
               if (versionCompare(version, pyangVersion) != 0) {
                  getLog().warn("Recommended pyang version " + pyangVersion + " using " + version);
               }
            }
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
      if(excludes != null && excludes.length > 0){
         collection.setExcludes(excludes);
      }else {
         collection.setExcludes(DEFAULT_EXCLUDES);
      }
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

   public abstract String getCacheFile();

   private void storeFileHashCache(Properties props) {
      String cacheFileName = getCacheFile();
      if(null == cacheFileName){
         return;
      }
      File cacheFile = new File(targetDirectory, cacheFileName);
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
      String cacheFileName = getCacheFile();
      if(null == cacheFileName){
         return null;
      }
      File cacheFile = new File(targetDirectory, cacheFileName);
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
    *  @param file
    * @param resultCollector
    * @param hashCache
    * @param basedirPath
    */
   private void performOperation(File file, ResultCollector resultCollector, Properties hashCache,
                  String basedirPath) {
      try {
         doOperation(file, resultCollector, hashCache, basedirPath);
      } catch (IOException e) {
         resultCollector.failCount++;
         getLog().warn(e);
      } catch (BadLocationException e) {
         resultCollector.failCount++;
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
    * @param resultCollector
    * @param hashCache
    * @param basedirPath
    * @throws IOException
    * @throws BadLocationException
    */
   private void doOperation(File file, ResultCollector resultCollector, Properties hashCache,
                  String basedirPath)
                  throws IOException, BadLocationException {
      Log log = getLog();
      log.info("Processing file: " + file);

      String code = readFileAsString(file);
      String originalHash = md5hash(code);

      String canonicalPath = file.getCanonicalPath();
      String path = canonicalPath.substring(basedirPath.length());
      if(hashCache != null) {
         String cachedHash = hashCache.getProperty(path);
         if (cachedHash != null && cachedHash.equals(originalHash)) {
            resultCollector.skippedCount++;
            return;
         }
      }

      executeOperation(file, resultCollector, hashCache, log, originalHash, path);

   }

   /**
    *
    * @param file
    * @param resultCollector
    * @param hashCache
    * @param log
    * @param originalHash
    * @param path
    * @return
    * @throws IOException
    */
   private boolean executeOperation(File file, ResultCollector resultCollector,
                  Properties hashCache, Log log, String originalHash, String path){

      try {
         if(performOperation(file)){
            resultCollector.successCount++;
         }else{
            resultCollector.failCount++;
         }
      } catch (IOException e) {
         e.printStackTrace();
         resultCollector.failCount++;
      } catch (RuntimeException re){
         resultCollector.failCount++;
         if(failOnError) {
            throw re;
         }
      }
      if(hashCache != null) {
         hashCache.setProperty(path, originalHash);
      }
      return true;
   }

   public boolean isWindows(){
      String osName = System.getProperty("os.name");
      return osName.startsWith("Windows");
   }
   
   public abstract String[] getArguments();
   
   /**
    * get the command string. 
    * Based on the ostype this can vary. right now supported only for windows. 
    * @param operation
    * @return
    */
   public List<String>  getCommandString(OperationType operation){
      List<String> commandBuilder = new ArrayList<String>();
      if(isWindows()){
         commandBuilder.addAll(Arrays.asList("cmd", "/c"));
      }
      commandBuilder.add("pyang");
      
      String[] arguments = getArguments();
      if(arguments != null && arguments.length > 0) {
         for(String arg : arguments){
            commandBuilder.add(arg);
         }
      }
      if(operation == OperationType.VERSION){
         commandBuilder.add("-v");
      } else if(operation == OperationType.FORMAT){
         commandBuilder.add("-f");
         commandBuilder.add("yang");
      } else if(operation == OperationType.CONVERT){
         commandBuilder.add("-f");
         commandBuilder.add("yin");
      }
      return commandBuilder;
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
   public void writeStringToFile(String str, File file) throws IOException {
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

   abstract boolean performOperation(File file)
                  throws IOException;

   class ResultCollector {
      private int successCount;
      private int failCount;
      private int skippedCount;
   }

   public enum OperationType {
      VERSION,
      COMPILE,
      CONVERT,
      FORMAT
   }

   public static void main(String args[]){
     /* YangConverter yangConverter = new YangConverter();
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
      System.out.println(file.getName());*/
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
