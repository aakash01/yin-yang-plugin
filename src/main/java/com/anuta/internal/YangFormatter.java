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
 * Format yang file. 
 *
 * @goal format
 * @requiresProject false
 */
public class YangFormatter extends YangHelperMojo {
   private static final String FORMAT_CACHE_PROPERTIES_FILENAME = "yang-format-cache.properties";

   /**
    * list of custom arguments which can be passed while converting yang to yin
    * Eg: --keep-comments
    *
    * @parameter
    */
   private String[] formatArgs;
   
   public void execute() throws MojoExecutionException, MojoFailureException {
      getLog().info("GOAL is " + OperationType.FORMAT);
      executeGoal(OperationType.FORMAT);
   }

   @Override
   public String getCacheFile() {
      return FORMAT_CACHE_PROPERTIES_FILENAME;
   }

   @Override
   public String[] getArguments() {
      return formatArgs;
   }

   
   @Override
   public boolean performOperation(File file)
                  throws IOException {
      StringBuilder result = new StringBuilder();
      try {
         List<String> commandString = getCommandString(OperationType.FORMAT);
         commandString.add(file.getCanonicalPath());
         ProcessBuilder pb = new ProcessBuilder(commandString);
         pb.directory(file.getParentFile());
         if(null != getYangMODPath()){
            pb.environment().put("YANG_MODPATH",getYangMODPath());
         }
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
            getLog().error("Failed to format file "+file.getName());
            getLog().error(errorBuilder.toString());
            throw new RuntimeException(errorBuilder.toString());
         }
      } catch (InterruptedException e) {
         e.printStackTrace();
      }

      if (result == null) {
         return false;
      }
      
      String formattedCode = result.toString();
      writeStringToFile(formattedCode, file);
      return true;

   }
}
