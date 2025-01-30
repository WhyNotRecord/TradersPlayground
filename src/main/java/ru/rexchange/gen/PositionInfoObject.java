package ru.rexchange.gen;

import ru.rexchange.exception.SystemException;
import ru.rexchange.exception.UserException;

import java.sql.Connection;
import java.sql.SQLException;

public class PositionInfoObject extends PositionInfo {


  public enum PositionStatus {
    NEW,//TODO обеспечить создание позиций с лимитными ордерами на статусе NEW, переход на OPEN при срабатывании
    OPEN,//todo позиции с рыночными ордерами сразу должны создаваться на OPEN
    CLOSED,
    CANCELLED,//todo переименовать в CANCELED
    ERROR;
  }

  public static PositionInfoObject createAndLoad(String traderId, String id, Connection conn)
      throws SQLException, UserException, SystemException {
    PositionInfoObject result = new PositionInfoObject();
    result.setTraderId(traderId);
    result.setPositionId(id);
    result.load(conn);
    return result;
  }

  public static PositionInfoObject createNew(String traderId, String positionId) {
    PositionInfoObject result = new PositionInfoObject();
    result.setTraderId(traderId);
    result.setPositionId(positionId);
    return result;
  }

  public boolean onStatusNew() {
    return PositionStatus.NEW.name().equals(getStatus());
  }

  public boolean onStatusOpen() {
    return PositionStatus.OPEN.name().equals(getStatus());
  }

  @Override
  public String toString() {
    return getDirection() + " position (" + getStatus() + ") on " +
        getSymbol() + " for " + getAmount() + " at " + getAveragePrice();
  }
}
