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
 * To Compile yang files. 
 *
 * @goal compile
 * @requiresProject false
 */
public class YangCompiler extends YangHelperMojo {
   private static final String COMPILE_CACHE_PROPERTIES_FILENAME = "yang-compile-cache.properties";

   /**
    * list of custom arguments which can be passed while compiling yang
    * Eg: --keep-comments
    *
    * @parameter
    */
   private String[] compileArgs;
   
   public void execute() throws MojoExecutionException, MojoFailureException {
      getLog().info("GOAL is " + OperationType.COMPILE);
      executeGoal(OperationType.COMPILE);
   }

   @Override
   public String getCacheFile() {
      return COMPILE_CACHE_PROPERTIES_FILENAME;
   }

   @Override
   public String[] getArguments() {
      return compileArgs;
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
            getLog().error("Failed to compile file " + file.getName());
            getLog().error(errorBuilder.toString());
               throw new RuntimeException(errorBuilder.toString());
         }
      } catch (IOException e) {
         e.printStackTrace();
      } catch (InterruptedException e) {
         e.printStackTrace();
      }

      if (result == null) {
         return false;
      }
      return true;

   }
}
