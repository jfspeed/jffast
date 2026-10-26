/*
 * Copyright 2024-2026 jffast
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.jfspeed.common.core.util;

/**
 * @author jffast
 * @date 2024/2/1 14:42
 * @desc
 */
import java.io.File;

import java.io.FileFilter;

import java.io.IOException;

import java.net.JarURLConnection;

import java.net.URL;

import java.net.URLDecoder;

import java.util.ArrayList;

import java.util.Enumeration;

import java.util.List;

import java.util.jar.JarEntry;

import java.util.jar.JarFile;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j


public class ClassUtil {

    /**
     * 取得某个接口下所有实现这个接口的类
     */
    public static List getAllClassByInterface(Class c) {

        List  returnClassList = null;

        if(c.isInterface()) {

// 获取当前的包*

            String packageName = c.getPackage().getName();

// 获取当前包下以及子包下所以的*

            List<Class> allClass = getClasses(packageName).stream().map(t-> {
                try {
                    return Class.forName(t);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());

            if(allClass != null) {

                returnClassList = new ArrayList();

                for(Class classes : allClass) {

// 判断是否是同一个接*

                    if(c.isAssignableFrom(classes)) {

// 本身不加入进*

                        if(!c.equals(classes)) {

                            returnClassList.add(classes);

                        }

                    }

                }

            }

        }

        return returnClassList;

    }

    /*
     * 取得某一类所在包的所有类*不含迭代
     */

    public static String[] getPackageAllClassName(String classLocation, String packageName){

//将packageName分解

        String[] packagePathSplit = packageName.split("[.]");

        String realClassLocation = classLocation;

        int packageLength = packagePathSplit.length;

        for(int i = 0; i< packageLength; i++){

            realClassLocation = realClassLocation + File.separator+packagePathSplit[i];

        }

        File packeageDir = new File(realClassLocation);

        if(packeageDir.isDirectory()){

            String[] allClassName = packeageDir.list();

            return allClassName;

        }

        return null;

    }

/**
 * 从包package中获取所有的Class
 * @param packageName
 * @return
 */

    public static List<String> getClasses(String packageName){
//第一个class类的集合
        List<String> classes = new ArrayList<>();
//是否循环迭代
        boolean recursive = true;
//获取包的名字 并进行替*
        String packageDirName = packageName.replace('.', '/');
//定义一个枚举的集合 并进行循环来处理这个目录下的things
        Enumeration dirs;
        try {
            dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
//循环迭代下去
            while (dirs.hasMoreElements()){
//获取下一个元*
                URL url = (URL) dirs.nextElement();
//得到协议的名*
                String protocol = url.getProtocol();
//如果是以文件的形式保存在服务器上
                if ("file".equals(protocol)) {
//获取包的物理路径
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
//以文件的方式扫描整个包下的文*并添加到集合*
                    findAndAddClassesInPackageByFile(packageName, filePath, recursive, classes);
                } else if ("jar".equals(protocol)){
//如果是jar包文*
//定义一个JarFile
                    JarFile jar;
                    try {
//获取jar
                        jar = ((JarURLConnection) url.openConnection()).getJarFile();
//从此jar*得到一个枚举类
                        Enumeration entries = jar.entries();
//同样的进行循环迭*
                        while (entries.hasMoreElements()) {
//获取jar里的一个实*可以是目*和一些jar包里的其他文*如META-INF等文*
                            JarEntry entry = (JarEntry) entries.nextElement();
                            String name = entry.getName();
//如果是以/开头的
                            if (name.charAt(0) == '/') {
//获取后面的字符串
                                name = name.substring(1);
                            }

//如果前半部分和定义的包名相同
                            if (name.startsWith(packageDirName)) {
                                int idx = name.lastIndexOf('/');
//如果*/"结尾 是一个包
                                if (idx != -1) {
//获取包名 */"替换*."
                                    packageName = name.substring(0, idx).replace('/', '.');
                                }
//如果可以迭代下去 并且是一个包
                                if ((idx != -1) || recursive){
//如果是一*class文件 而且不是目录
                                    if (name.endsWith(".class") && !entry.isDirectory()) {
//去掉后面*.class" 获取真正的类*
                                        String className = name.substring(packageName.length() + 1, name.length() - 6);
                                        try {
//添加到classes
//                                            classes.add(Class.forName(packageName + '.' + className));
                                            classes.add(packageName + '.' + className);
                                        } catch (Exception e) {
            log.error("Operation failed", e);
                                        }
                                    }
                                }
                            }

                        }

                    } catch (IOException e) {
                        log.error("Operation failed", e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Operation failed", e);
        }
        return classes;

    }

    /**
     * 以文件的形式来获取包下的所有Class
     * @param packageName
     * @param packagePath
     * @param recursive
     * @param classes
     */

    public static void findAndAddClassesInPackageByFile(String packageName, String packagePath, final boolean recursive, List<String> classes){

//获取此包的目*建立一个File

        File dir = new File(packagePath);

//如果不存在或*也不是目录就直接返回

        if (!dir.exists() || !dir.isDirectory()) {

            return;

        }

//如果存在 就获取包下的所有文*包括目录

        File[] dirfiles = dir.listFiles(new FileFilter() {

//自定义过滤规*如果可以循环(包含子目* 或则是以.class结尾的文*编译好的java类文*

            
            public boolean accept(File file) {

                return (recursive && file.isDirectory()) || (file.getName().endsWith(".class"));

            }

        });

//循环所有文*

        for (File file : dirfiles) {

//如果是目*则继续扫*

            if (file.isDirectory()) {

                findAndAddClassesInPackageByFile(packageName + "." + file.getName(),

                        file.getAbsolutePath(),

                        recursive,

                        classes);

            }

            else {

//如果是java类文*去掉后面*class 只留下类*

                String className = file.getName().substring(0, file.getName().length() - 6);

                try {

//添加到集合中*

//                    classes.add(Class.forName(packageName + '.' + className));
                    classes.add(packageName + '.' + className);

                } catch (Exception e) {
            log.error("Operation failed", e);

                }

            }

        }

    }

}
