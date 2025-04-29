package ru.rexchange.tools;

import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rexchange.exception.SystemException;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Utils {
  private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
  private static final String STOP_REQUEST_FILE = "STOP.txt";

  public static String formatFloatValue(float value, int maxPrecision) {
    DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    df.setMaximumFractionDigits(maxPrecision); //340 = DecimalFormat.DOUBLE_FRACTION_DIGITS

    return df.format(value);
  }

  public static boolean checkIfStopRequested(Logger logger) {
    File stopFile = new File(STOP_REQUEST_FILE);
    if (stopFile.exists()) {
      logger.info("Обнаружен стоп-файл. Завершение работы...");
      if (!stopFile.delete())
        logger.warn("Не удалось удалить стоп-файл. Он может помешать последующим запускам программы");
      return true;
    }
    return false;
  }

  public static void configureLogging() {
    configureLogging(false);
  }

  public static void configureLogging(boolean test) {
    // import org.apache.logging.log4j.core.LoggerContext;
    Configurator.initialize(null, test ? "log4j2-test.xml" : "log4j2.xml");
    Configurator.reconfigure();
    /*LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
    File file = new File(test ? "log4j2-test.xml" : "log4j2.xml");

    // this will force a reconfiguration
    context.setConfigLocation(file.toURI());*/
  }

  public static void waitSafe(long timeout) {
    waitSafe(timeout, LOGGER);
  }

  public static void waitSafe(long timeout, Logger logger) {
    waitSafe(timeout, logger, null);
  }

  public static void waitSafe(long timeout, Logger logger, String caption) {
    String message = "Waiting for " + timeout +
        " (" + DateUtils.formatTime(DateUtils.currentTimeMillis() + timeout) + ")";
    if (caption != null)
      message = caption + ". " + message;
    logger.debug(message);
    try {
      Thread.sleep(timeout);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public static float[] getTwoLastValues(LinkedList<Float> list) {
    Iterator<Float> it = list.descendingIterator();
    Float last = null;
    for (int i = 0; i < 2 && it.hasNext(); i++) {
      if (last == null)
        last = it.next();
      else {
        float prev = it.next();
        return new float[] {prev, last};
      }
    }
    return null;
  }

  public static <T> T executeInFewAttempts(Callable<T> p, int attempts, long pause) throws Exception {
    long to = pause;
    Exception last = null;
    for (int i = 0; i < attempts; i++) {
      try {
        return p.call();
      } catch (Exception e) {
        LOGGER.error(e.getMessage());
        Utils.waitSafe(to);
        to *= 2L;
        last = e;
      }
    }
    throw last;
    /*if (last != null)
      LOGGER.warn("Last error:", last);
    return null;*/
  }

  public static <T> T executeInFewAttemptsSafe(Callable<T> p, int attempts, long pause, Logger logger) {
    long to = pause;
    Exception last = null;
    for (int i = 0; i < attempts; i++) {
      try {
        return p.call();
      } catch (Exception e) {
        logger.error(e.getMessage());
        Utils.waitSafe(to);
        to *= 2L;
        last = e;
      }
    }
    logger.error("Last attempt error", last);
    return null;
  }

  public static boolean waitForCondition(Callable<Boolean> p, int attempts, long pause, Logger logger, String caption) {
    if (logger == null)
      logger = LOGGER;
    for (int i = 0; i < attempts; i++) {
      try {
        if (!Boolean.TRUE.equals(p.call())) {
          Utils.waitSafe(pause, logger, caption);
        } else {
          return true;
        }
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }
    return false;
  }

  private static final AtomicInteger threadsCounter = new AtomicInteger(1);
  public static void runInSeparateThread(Runnable task, String name, Logger logger) {
    String threadName = "thr-" + name + "-" + threadsCounter.incrementAndGet();
    Thread thread = new Thread(task, threadName) {
      @Override
      public void run() {
        LOGGER.info("Thread {} started", threadName);
        super.run();
        LOGGER.info("Thread {} finished", threadName);
      }
    };
    thread.setUncaughtExceptionHandler((t, e) -> {
      logger.error("Exception in {}", t.getName(), e);
    });
    thread.start();
  }

  public static void runInSeparateThreads(Collection<Runnable> tasks, String name, Logger logger) {
    /*TODO run all tasks, wait for finish Thread thread = new Thread(task, "custom-thread-" + name);
    thread.setUncaughtExceptionHandler((t, e) -> {
      logger.error("Exception in thread " + t.getName(), e);
    });
    thread.start();*/
  }

  public static String extractParamValue(String[] params, String paramName) {
    for (int i = 0; i < params.length - 1; i++) {
      if (params[i].equals(paramName))
        return params[i + 1];
    }
    throw new SystemException("Cannot find param \"%s\" value", paramName);
  }

  private static final Map<String, CachedEntity<?>> cachedObjects = new ConcurrentHashMap<>();

  public static <T> T getCachedObject(String name, long lifeTime, Evaluatable<T> getter) {
    // Сначала быстрая проверка: если объект есть и актуален, сразу возвращаем
    CachedEntity<?> existingEntity = cachedObjects.get(name);
    if (existingEntity != null && existingEntity.isFresh()) {
      return (T) existingEntity.value;
    }
    // Если объект отсутствует или просрочен, используем compute(...)  для атомарного обновления в кэше
    CachedEntity<?> newEntity = cachedObjects.compute(name, (key, oldEntity) -> {
      // Если окажется, что другой поток уже обновил объект, и он актуален, возвращаем старый (новый) объект
      if (oldEntity != null && oldEntity.isFresh()) {
        return oldEntity;
      }
      // Иначе — вызываем getter, кладём результат в кэш
      T newValue = getter.evaluate();
      return newValue != null ? new CachedEntity<>(newValue, lifeTime) : null;
    });

    // Если newEntity == null, значит либо результат evaluate() был null, либо явно возвращён null в compute(...)
    return newEntity == null ? null : (T) newEntity.value;

    /*if (cachedObjects.containsKey(name)) {
      CachedEntity<?> cachedEntity = cachedObjects.get(name);
      if (cachedEntity.isFresh()) {
        return (T) cachedEntity.value;
      } else {
        cachedObjects.remove(name);
      }
    }
    T newValue = getter.evaluate();
    if (newValue != null) {
      cachedObjects.put(name, new CachedEntity<>(newValue, lifeTime));
    }
    return newValue;*/
  }

  public static boolean hasParam(String[] params, String paramName) {
    for (String param : params) {
      if (param.equals(paramName))
        return true;
    }
    return false;
  }

  public interface Evaluatable<T> {
    T evaluate();
  }

  private static class CachedEntity<T> {
    long timestamp = -1L;
    long lifeTime = 1000L;
    T value = null;

    public CachedEntity(T value, long lifeTime) {
      this.value = value;
      this.timestamp = DateUtils.currentTimeMillis();
      this.lifeTime = lifeTime;
    }

    public boolean isFresh() {
      return DateUtils.currentTimeMillis() - timestamp < lifeTime;
    }
  }
}
