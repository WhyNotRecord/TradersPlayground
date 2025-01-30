package ru.rexchange.data;

public class Consts {
  public static final String FEAR_AND_GREED_INDEX = "FAG";
  public static final String PERSONAL_CHAT_ID = "82438718";
  public static final String CHANNEL_CHAT_ID = "-1001968512635";
  public static final String SAFETY_ORDER_FLAG = "safety";
  public static final String BUY = "BUY";
  public static final String SELL = "SELL";
  public static final String PARAMETER_APPC = "APPC";

  public interface Direction {
    int NEUTRAL = 0;// Движения нет
    int DOWN = -1;// Движение вниз
    int UP = 1;// Движение вверх
  }

  //todo getFibo(int index)
}
