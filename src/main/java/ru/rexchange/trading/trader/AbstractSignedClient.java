package ru.rexchange.trading.trader;

public abstract class AbstractSignedClient {
  //public abstract void setPositionMode(boolean hedge) throws Exception;

  public abstract boolean canWithdraw() throws Exception;
}
