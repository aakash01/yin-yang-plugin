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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Created by Aakash on 10/12/2015.
 * @author aakash01.nitb@gmail.com
 */

/**
 *  Convert yang to yin. 
 *
 * @goal convert
 * @requiresProject false
 */
public class YangConverter extends YangHelperMojo {
   private static final String CONVERT_CACHE_PROPERTIES_FILENAME = "yin-yang-convert-cache.properties";

   /**
    * list of custom arguments which can be passed while converting yang to yin
    * Eg: --yin-pretty-strings
    *
    * @parameter
    */
   private String[] convertArgs;
   
   public void execute() throws MojoExecutionException, MojoFailureException {
      getLog().info("GOAL is " + OperationType.CONVERT);
      executeGoal(OperationType.CONVERT);
   }

   @Override
   public OperationType getOperation() {
      return OperationType.CONVERT;
   }

   @Override
   public String getCacheFile() {
      return CONVERT_CACHE_PROPERTIES_FILENAME;
   }

   @Override
   public String[] getArguments() {
      return convertArgs;
   }

   @Override
   boolean performOperation(File file) throws IOException {

         StringBuilder result = new StringBuilder();
         try {
            List<String> commandString = getCommandString(OperationType.CONVERT);
            commandString.add(file.getCanonicalPath());
            ProcessBuilder pb = new ProcessBuilder(commandString);
            pb.directory(file.getParentFile());
            if(null != getYangMODPath()){
               pb.environment().put("YANG_MODPATH",getYangMODPath());
            }
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
               getLog().error("Failed to convert file "+file.getName());
               getLog().error(errorBuilder.toString());
               throw new RuntimeException(errorBuilder.toString());
            }
         } catch (InterruptedException e) {
            e.printStackTrace();
         }

         if (result == null) {
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
}
