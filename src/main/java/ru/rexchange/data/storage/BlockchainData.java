package ru.rexchange.data.storage;

public class BlockchainData extends AbstractRate<Float> {
  private final long timestamp;
  public BlockchainData(long time) {
    this.timestamp = time;
  }

  @Override
  public Float getValue() {
    return null;
  }

  @Override
  public Float getTypical() {
    return null;
  }

  @Override
  public Float getRelativeChange() {
    return null;
  }

  @Override
  public long getOpenTime() {
    return 0;
  }

  @Override
  public long getCloseTime() {
    return 0;
  }
}
