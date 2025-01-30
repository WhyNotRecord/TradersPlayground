package ru.rexchange.trading;

import ru.rexchange.gen.ApiConfig;

public class TraderAuthenticator {
  private static final String AUTH_CREDS_CIPHER = "auth_credentials";
  private final String name;
  private final String publicKey;
  private final String privateKey;
  private final String personalKey;
  private ApiConfig apiConfigObject = null;

  public TraderAuthenticator(String name, String publicKey, String privateKey) {
    this.publicKey = publicKey;
    this.privateKey = privateKey;
    this.personalKey = null;
    this.name = name;
  }

  public TraderAuthenticator(String name, String publicKey, String privateKey, String personalKey) {
    this.publicKey = publicKey;
    this.privateKey = privateKey;
    this.personalKey = personalKey;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public String getPersonalKey() {
    return personalKey;
  }

  public boolean isFromDB() {
    return apiConfigObject != null;
  }

  @Override
  public String toString() {
    return super.toString() + "#" + this.name;
  }

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj);
  }
}
