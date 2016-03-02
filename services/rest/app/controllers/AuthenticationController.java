/**
 * 
 */
package controllers;

import java.util.List;

import org.ats.common.PageList;
import org.ats.services.OrganizationContext;
import org.ats.services.organization.TenantService;
import org.ats.services.organization.UserService;
import org.ats.services.organization.base.AuthenticationService;
import org.ats.services.organization.entity.Tenant;
import org.ats.services.organization.entity.User;
import org.ats.services.organization.entity.fatory.ReferenceFactory;
import org.ats.services.organization.entity.fatory.UserFactory;
import org.ats.services.organization.entity.reference.TenantReference;

import play.Logger;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import actions.CorsComposition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Mar 20, 2015
 */
@CorsComposition.Cors
public class AuthenticationController extends Controller {
  
  @Inject
  private AuthenticationService<User> authenService;
  
  @Inject
  private OrganizationContext context;
  
  @Inject
  private TenantService tenantService;
  
  @Inject
  private UserFactory userFactory;
  
  @Inject
  private ReferenceFactory<TenantReference> tenantRef;
  
  @Inject
  private UserService userService;
  
  public Result checkAccount() {
    
    String email = request().getQueryString("email");
    
    Logger.info("Check account in controller");
    if (userService.get(email) != null) {
      return ok("false");
    }
    return ok();
  }
  public Result login() {
    JsonNode json = request().body().asJson();

    String email = json.get("email").asText();
    String password = json.get("password").asText();
    
    String token = authenService.logIn(email, password);
    if (token == null) return unauthorized();
    
    ObjectNode authTokenJson = Json.newObject();
    authTokenJson.put(AuthenticationService.AUTH_TOKEN, token);
    return ok(authTokenJson);
  }
  
  public Result logout() {
    authenService.logOut();
    return ok();
  }
  
  public Result register() {
    
    JsonNode json = request().body().asJson();
    
    String email = json.get("email").asText();
    if (userService.get(email) != null) {
      return status(401);
    }
    String password = json.get("password").asText();
    JsonNode tenant = json.get("tenant");
    String tenantId = tenant.get("_id").asText();
    
    String firstName = json.get("firstname").asText();
    String lastName = json.get("lastname").asText();
    User user = userFactory.create(email, firstName, lastName);
    
    /*if (json.get("space").asText() != null && !("".equals(json.get("space").asText()))) {
      spaceName = json.get("space").asText();
      
      Space space = spaceFactory.create(spaceName);
      
      space.setTenant(tenantRef.create(tenantId));
      
      spaceService.create(space);
      user.joinSpace(spaceRef.create(space.getId()));
    }*/
    
    user.setTenant(tenantRef.create(tenantId));
    user.setPassword(password);
    
    userService.create(user);
    
    String token = authenService.logIn(email, password);
    ObjectNode authTokenJson = Json.newObject();
    authTokenJson.put(AuthenticationService.AUTH_TOKEN, token);
    
    return ok(authTokenJson);
  }
  
  public Result tenants() {
    
    PageList<Tenant> list = tenantService.list();
    
    ArrayNode array = Json.newObject().arrayNode();
    
    while (list.hasNext()) {
      List<Tenant> listUser = list.next();
      
      for (Tenant tenant : listUser) {
        ObjectNode json = Json.newObject();
        json.put("_id", tenant.getId());
        array.add(json);
      }
    }
    
    return ok(array);
  }
  
  public Result current() {
    ObjectNode json = Json.newObject();
    
    if (context.getUser() != null)
      json.put("user", Json.parse(context.getUser().toString()));
    
    if (context.getTenant() != null) 
      json.put("tenant", Json.parse(context.getTenant().toString()));
    
    if (context.getSpace() != null)
      json.put("space", Json.parse(context.getSpace().toString()));
    return ok(json);
  }
}
