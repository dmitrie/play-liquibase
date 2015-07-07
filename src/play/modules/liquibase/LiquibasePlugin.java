package play.modules.liquibase;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.exception.ValidationFailedException;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.hibernate.JDBCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Play;
import play.PlayPlugin;
import play.utils.Properties;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LiquibasePlugin extends PlayPlugin {

  private static final Logger logger = LoggerFactory.getLogger(LiquibasePlugin.class);

  @Override
  public void onApplicationStart() {

    String autoUpdate = Play.configuration.getProperty("liquibase.active", "false");
    String changeLogPath = Play.configuration.getProperty("liquibase.changelog", "mainchangelog.xml");
    String propertiesPath = Play.configuration.getProperty("liquibase.properties", "liquibase.properties");
    String scanner = Play.configuration.getProperty("liquibase.scanner", "jar");
    String contexts = Play.configuration.getProperty("liquibase.contexts", null);
    contexts = (null != contexts && !contexts.trim().isEmpty()) ? contexts : null;
    String actions = Play.configuration.getProperty("liquibase.actions");

    if (null == actions) {
      throw new LiquibaseUpdateException("No valid action found for liquibase operation");
    }

    ResourceAccessor accessor = null;
    if ("jar".equals(scanner)) {
      accessor = new DuplicatesIgnorantResourceAccessor(Play.classloader);
    }
    else if ("src".equals(scanner)) {
      accessor = new FileSystemResourceAccessor(Play.applicationPath.getAbsolutePath());
    }
    else {
      throw new LiquibaseUpdateException("No valid scanner found liquibase operation " + scanner);
    }

    List<LiquibaseAction> acts = new ArrayList<LiquibaseAction>();

    for (String action : actions.split(",")) {
      LiquibaseAction op = LiquibaseAction.valueOf(action.toUpperCase());
      acts.add(op);
    }

    Database db = null;

    if (true == Boolean.valueOf(autoUpdate)) {

      logger.info("Auto update flag found and positive => let's get on with changelog update");
      InputStream pstream = null;
      InputStream clstream = null;

      try {

        Connection cnx = getConnection();

        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(cnx));

        final Liquibase liquibase = new Liquibase(changeLogPath, accessor, database);
        if ("jar".equals(scanner)) {
          pstream = Play.classloader.getResourceAsStream(propertiesPath);
        }
        else {
          pstream = new FileInputStream(Play.getFile(propertiesPath));
        }

        if (null != pstream) {
          Properties props = new Properties();
          props.load(pstream);

          for (String key : props.keySet()) {
            String val = props.get(key);
            logger.info("found parameter [{}] / [{}] for liquibase update", key, val);
            liquibase.setChangeLogParameter(key, val);
          }
        }
        else {
          logger.info("Could not find properties file [{}]", propertiesPath);
        }

        db = liquibase.getDatabase();
        for (LiquibaseAction op : acts) {
          logger.info("Dealing with op [{}]", op);

          switch (op) {
            case LISTLOCKS:
              liquibase.reportLocks(System.out);
              break;
            case RELEASELOCKS:
              liquibase.forceReleaseLocks();
              break;
            case SYNC:
              liquibase.changeLogSync(contexts);
              break;
            case STATUS:
              File tmp = Play.tmpDir.createTempFile("liquibase", ".status");
              liquibase.reportStatus(true, contexts, new FileWriter(tmp));
              logger.info("status dumped into file [{}]", tmp);
              break;
            case UPDATE:
              liquibase.update(contexts);
              break;
            case CLEARCHECKSUMS:
              liquibase.clearCheckSums();
              break;
            case VALIDATE:
              try {
                liquibase.validate();
              }
              catch (ValidationFailedException e) {
                logger.error("liquibase validation error", e);
              }
            default:
              break;
          }
          logger.info("op [{}] performed", op);
        }
      }
      catch (SQLException | LiquibaseException | IOException sqe) {
        throw new LiquibaseUpdateException(sqe.getMessage(), sqe);
      }
      finally {
        if (null != db) {
          try {
            db.close();
          }
          catch (DatabaseException | JDBCException e) {
            logger.warn("problem closing connection: " + e, e);
          }
        }
        if (null != pstream) {
          try {
            pstream.close();
          }
          catch (Exception e) {
            logger.warn("problem closing pstream: " + e, e);
          }
        }
        if (null != clstream) {
          try {
            clstream.close();
          }
          catch (Exception e) {
            logger.warn("problem closing clstream: " + e, e);
          }
        }
      }

    }
    else {
      logger.info("Auto update flag [{}] != true  => skipping structural update", autoUpdate);
    }
  }

  @SuppressWarnings("CallToDriverManagerGetConnection")
  private Connection getConnection() throws SQLException {
    String url = Play.configuration.getProperty("db.url");
    String username = Play.configuration.getProperty("db.user");
    String password = Play.configuration.getProperty("db.pass");
    logger.info("Migrate DB: {} @ {}", username, url);
    return DriverManager.getConnection(url, username, password);
  }
}
