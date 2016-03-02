/**
 * 
 */
package org.ats.services.vmachine;

import java.util.logging.Logger;

import org.ats.common.PageList;
import org.ats.services.data.MongoDBService;
import org.ats.services.organization.base.AbstractMongoCRUD;
import org.ats.services.organization.entity.fatory.ReferenceFactory;
import org.ats.services.organization.entity.reference.SpaceReference;
import org.ats.services.organization.entity.reference.TenantReference;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Jul 2, 2015
 */
@Singleton
public class VMachineService extends AbstractMongoCRUD<VMachine>{

  /** .*/
  private final String COL_NAME = "vm";
  
  @Inject
  private VMachineFactory vmFactory;
  
  @Inject
  private ReferenceFactory<TenantReference> tenantRefFactory;
  
  @Inject
  private ReferenceFactory<SpaceReference> spaceRefFactory;
  
  @Inject
  VMachineService(MongoDBService mongo, Logger logger) {
    this.col = mongo.getDatabase().getCollection(COL_NAME);
    this.logger = logger;
    
    this.col.createIndex(new BasicDBObject("tenant._id", 1));
    this.col.createIndex(new BasicDBObject("space._id", 1));
  }
  
  public VMachine getSystemVM(TenantReference tenantRef, SpaceReference spaceRef) {
    return findOne(tenantRef, spaceRef, true, false);
  }
  
  public VMachine getTestVMAvailabel(TenantReference tenantRef, SpaceReference spaceRef, boolean hasUI) {
    return findOneAvailable(tenantRef, spaceRef, false, hasUI);
  }
  
  public VMachine findOneAvailable(TenantReference tenantRef, SpaceReference spaceRef, boolean system, boolean hasUI) {
    return findOne(tenantRef, spaceRef, system, hasUI, VMachine.Status.Started);
  }
  
  public VMachine findOne(TenantReference tenantRef, SpaceReference spaceRef, boolean system, boolean hasUI, VMachine.Status status) {
    BasicDBObject query = new BasicDBObject("tenant", tenantRef.toJSon());
    query.put("space", spaceRef == null ? null : spaceRef.toJSon());
    query.put("system", system);
    query.put("ui", hasUI);
    query.put("status", status.toString());
    
    PageList<VMachine> list = query(query);
    return list.count() > 0 ? list.next().get(0) : null;
  }
  
  public VMachine findOne(TenantReference tenantRef, SpaceReference spaceRef, boolean system, boolean hasUI) {
    BasicDBObject query = new BasicDBObject("tenant", tenantRef.toJSon());
    query.put("space", spaceRef == null ? null : spaceRef.toJSon());
    query.put("system", system);
    query.put("ui", hasUI);
    
    PageList<VMachine> list = query(query);
    return list.count() > 0 ? list.next().get(0) : null;
  }
  
  @Override
  public VMachine transform(DBObject source) {
    BasicDBObject obj = (BasicDBObject) source.get("tenant");
    TenantReference tenant = tenantRefFactory.create(obj.getString("_id"));
    
    obj = (BasicDBObject) source.get("space");
    SpaceReference space = obj == null ? null : spaceRefFactory.create(obj.getString("_id")); 
    
    boolean isSystem = ((BasicDBObject) source).getBoolean("system");
    boolean hasUI = ((BasicDBObject) source).getBoolean("ui");
    
    String publicIp = ((BasicDBObject) source).getString("public_ip");
    String privateIp = ((BasicDBObject) source).getString("private_ip");
    
    VMachine.Status status = source.get("status") == null ? null : VMachine.Status.valueOf((String) source.get("status"));
    
    VMachine machine = vmFactory.create((String) source.get("_id"), tenant, space, isSystem, hasUI, publicIp, privateIp, status);
    return machine;
  }

}
