package ru.rexchange.tools;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

@Plugin(name = "PackageLoggingFilter", category = Node.CATEGORY, printObject = true)
public class PackageLoggingFilter extends AbstractFilter {
  public static final String TRACE_LEVEL = "TRACE";
  private final String packageName;
  private final Level level;
  private final Boolean inverse;

  private PackageLoggingFilter(String packageName, Level level, Boolean inverse,
                               final Result onMatch, final Result onMismatch) {
    super(onMatch, onMismatch);
    this.packageName = packageName;
    this.level = level;
    this.inverse = inverse;//todo inverse logic
  }

  private Result filter(String loggerName, Level level) {
    if (level.isLessSpecificThan(this.level)) {
      if (loggerName.startsWith(packageName)) {
        return onMatch;
      } else {
        return onMismatch;
      }
    }
    return onMatch;
  }

  @Override
  public Result filter(LogEvent event) {
    return filter(event.getLoggerName(), event.getLevel());
  }

  @Override
  public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
    return filter(logger.getName(), level);
  }

  @Override
  public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
    return filter(logger.getName(), level);
  }

  @Override
  public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
    return filter(logger.getName(), level);
  }

  @Override
  public Result filter(Logger logger, Level level, Marker marker, String msg) {
    return filter(logger.getName(), level);
  }

  @PluginFactory
  public static PackageLoggingFilter createFilter(
      @PluginAttribute("packageName") String packageName,
      @PluginAttribute(value = "level", defaultString = TRACE_LEVEL) Level level,
      @PluginAttribute(value = "inverse", defaultBoolean = false) Boolean inverse,
      @PluginAttribute("onMatch") Result match,
      @PluginAttribute("onMismatch") Result mismatch) {
    return new PackageLoggingFilter(packageName, level, inverse, match, mismatch);
  }

  /*@PluginFactory
  public static PackageLoggingFilter createFilter(
      @PluginAttribute("packageName") String packageName,
      @PluginAttribute("onMatch") Result match,
      @PluginAttribute("onMismatch") Result mismatch) {
    return new PackageLoggingFilter(packageName, Level.TRACE, match, mismatch);
  }*/


  /*private Result filter(final Level testLevel) {
    return testLevel.isMoreSpecificThan(this.level) ? this.onMatch : this.onMismatch;
  }*/
}
