/*
 * Copyright (c) 2012, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.primeframework.gradle.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The database plugin is used for creating database
 *
 * @author James Humphrey
 */
class DatabasePlugin implements Plugin<Project> {

  Project project;

  def void apply(Project project) {

    this.project = project

    project.extensions.add("database", new DatabasePluginConfiguration())

    project.configurations {
      databasePlugin
    }

    project.dependencies {
      databasePlugin(
        "postgresql:postgresql:9.1-901-1.jdbc4",
        "mysql:mysql-connector-java:5.1.18"
      )
    }

    project.task("createDatabase") << {
      def databaseName = project.database.name
      if (databaseName == null) {
        databaseName = project.name.replace(".", "_").replace("-", "_")
      }

      project.database.name = databaseName
      setupDb(project, databaseName)
    }

    project.task("createDatabaseTest") << {
      def databaseTestName = project.database.testName
      if (databaseTestName == null) {
        databaseTestName = project.database.name
        if (databaseTestName == null) {
          databaseTestName = project.name.replace('.', '_').replace('-', '_')
        }
        databaseTestName += "_test"
      }

      project.database.testName = databaseTestName
      setupDb(project, databaseTestName)
    }
    
    project.test {
      dependsOn project.createDatabaseTest
    }
  }

  private void setupDb(Project project, def databaseName) {
    String propertyFilePath = "${project.gradle.gradleUserHomeDir}/plugins/database.properties"

    def props = loadProperties(propertyFilePath)
    def dbTypes = project.database.types
    if (dbTypes == null) {
      throw new GradleException("The project must define the database types it uses in a property named [databaseTypes]. " +
        "This property is a list of types defined as follows:\n\n" +
        "databaseTypes = [\"database.mysql\",\"database.postgresql\"]")
    }

    dbTypes.each {dbType ->
      def username = props["${dbType}.db.username"]
      def password = props["${dbType}.db.password"]
      if (username == null || password == null) {
        throw new GradleException("You must create a file named ${propertyFilePath} and add two properties " +
          "to it. The [" + dbType + ".db.username] property should contain the superuser username for your " +
          "database instance. The [" + dbType + ".db.password] property should contain the superuser password. " +
          "Each database might have different setup for this and you should consult the " +
          "database documentation to determine how to setup the superuser (sometimes called the root user).")
      }

      createDb(project, dbType, databaseName, username, password)
    }
  }

  private void createDb(Project project, DbType dbType, def databaseName, def username, def password) {
    if (dbType.equals(DbType.mysql)) {
      println "Creating MySQL database [${databaseName}]"

      def sql = """
          DROP DATABASE IF EXISTS ${databaseName};
          CREATE DATABASE `${databaseName}` CHARSET utf8 COLLATE utf8_bin;
          GRANT ALL PRIVILEGES on `${databaseName}`.* to '${project.database.username}'@'localhost' identified by '${project.database.password}';
          GRANT ALL PRIVILEGES on `${databaseName}`.* to '${project.database.username}'@'127.0.0.1' identified by '${project.database.password}';
      """

      executeSQL(DbType.mysql, DbType.mysql.rootDbName, sql, username, password, true, false)
    } else if (dbType.equals(DbType.postgresql)) {
      println "Creating PostgreSQL database [${databaseName}]"

      def sql = """
        DROP DATABASE IF EXISTS ${databaseName};
        CREATE DATABASE ${databaseName} ENCODING 'UTF-8' LC_CTYPE 'en_US.UTF-8' LC_COLLATE 'en_US.UTF-8' TEMPLATE template0;
        GRANT ALL PRIVILEGES ON DATABASE ${databaseName} TO ${project.database.username};
      """

      executeSQL(DbType.postgresql, DbType.postgresql.rootDbName, sql, username, password, true, false)
    }
  }

  private Properties loadProperties(String propertyFilePath) {
    def props = new Properties()
    def f = new File(propertyFilePath)
    if (f.isFile()) {
      f.withReader { reader ->
        props.load(reader);
      }
    }

    return props
  }

/**
 * Helper method for executing sql files
 *
 * @param dbType the database type
 * @param db the db
 * @param file the file to execute
 */
  public void executeSQLFile(def dbType, def db, def file, def username, def password, boolean autocommit,
                             boolean expandProperties) {
    def url = "jdbc:$dbType://localhost:$dbType.port/$db"

    println "Executing sql $dbType:$db < $file"

    project.ant.sql(
      src: file,
      driver: dbType.driver,
      url: url,
      userid: username,
      password: password,
      autocommit: autocommit,
      expandproperties: expandProperties) {

      classpath {
        pathElement(path: project.configurations.databasePlugin.asPath)
      }
    }
  }

/**
 * Helper method for executing sql files
 *
 * @param dbType the database type
 * @param db the db
 * @param file the file to execute
 */
  public void executeSQL(def dbType, def db, def sql, def username, def password, boolean autocommit,
                         boolean expandProperties) {
    def url = "jdbc:$dbType://localhost:$dbType.port/$db"

    println "Creating database $dbType:$db"

    project.ant.sql(
      sql,
      driver: dbType.driver,
      url: url,
      userid: username,
      password: password,
      autocommit: autocommit,
      expandproperties: expandProperties) {

      classpath {
        pathElement(path: project.configurations.databasePlugin.asPath)
      }
    }
  }

/**
 * Enum for representing db types
 */
  public static enum DbType {
    mysql("com.mysql.jdbc.Driver", 3306, "mysql"),
    postgresql("org.postgresql.Driver", 5432, "postgres")

    String driver
    int port
    String rootDbName

    DbType(String driver, int port, String rootDbName) {
      this.driver = driver
      this.port = port
      this.rootDbName = rootDbName
    }
  }

/**
 * Configuration bean
 */
  class DatabasePluginConfiguration {
    def username = "dev"
    def password = "dev"
    def testName
    def name
    def types
    def mysql = DbType.mysql
    def postgresql = DbType.postgresql
  }
}