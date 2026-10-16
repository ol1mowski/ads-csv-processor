package pl.oliwier.adscsv.api;

import java.util.UUID;

public final class ApiRoutes {

  public static final String AUTH = "/api/v1/auth";
  public static final String ME = AUTH + "/me";
  public static final String LOGIN = AUTH + "/login";
  public static final String LOGOUT = AUTH + "/logout";
  public static final String JOBS = "/api/v1/jobs";

  private ApiRoutes() {}

  public static String job(UUID id) {
    return JOBS + "/" + id;
  }

  public static String result(UUID id) {
    return job(id) + "/result";
  }
}
