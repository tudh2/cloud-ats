/**
 * 
 */
package org.ats.services.iaas;

import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpResponse;
import org.ats.common.http.HttpClientFactory;
import org.ats.common.http.HttpClientUtil;
import org.ats.services.iaas.exception.CreateVMException;
import org.ats.services.iaas.exception.DestroyTenantException;
import org.ats.services.iaas.exception.DestroyVMException;
import org.ats.services.iaas.exception.InitializeTenantException;
import org.ats.services.iaas.exception.RebuildVMException;
import org.ats.services.iaas.exception.StartVMException;
import org.ats.services.iaas.exception.StopVMException;
import org.ats.services.organization.entity.reference.SpaceReference;
import org.ats.services.organization.entity.reference.TenantReference;
import org.ats.services.vmachine.VMachine;
import org.ats.services.vmachine.VMachineFactory;
import org.ats.services.vmachine.VMachineService;

import com.google.inject.Inject;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Feb 23, 2016
 */
public class StandaloneService implements IaaSService {

  @Inject
  private VMachineService vmachineService;
  
  @Inject
  private VMachineFactory vmachineFactory;
  
  @Inject
  private Logger logger;
  
  @Override
  public void initTenant(TenantReference tenant) throws InitializeTenantException, CreateVMException {
    createSystemVM(tenant, null);
    logger.log(Level.INFO, "Created tenant " + tenant.getId());
  }

  @Override
  public void destroyTenant(TenantReference tenant) throws DestroyTenantException, DestroyVMException {
    throw new UnsupportedOperationException("Not supported on standalone mode");
  }

  @Override
  public VMachine createSystemVM(TenantReference tenant, SpaceReference space) throws CreateVMException {
    VMachine vm = vmachineService.getSystemVM(tenant, space);
    return vm == null ? createSystemVMAsync(tenant, space) : vm;
  }

  @Override
  public VMachine createSystemVMAsync(TenantReference tenant, SpaceReference space) throws CreateVMException {
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      String localAddress = InetAddress.getByName(hostname).getHostAddress();

      HttpResponse response = HttpClientUtil.execute(HttpClientFactory.getInstance(), "http://ipinfo.io/ip");
      String publicAddress = HttpClientUtil.getContentBodyAsString(response).trim();
      
      VMachine vm = vmachineFactory.create("standalone", tenant, space, true, false, publicAddress, localAddress, VMachine.Status.Started);
      vmachineService.create(vm);
      return vm;
    } catch (Exception e) {
      e.printStackTrace();
      throw new CreateVMException(e.getMessage());
    }
  }

  @Override
  public VMachine createTestVM(TenantReference tenant, SpaceReference space, boolean hasUI) throws CreateVMException {
    VMachine vm = vmachineService.getSystemVM(tenant, space);
    return vm == null ? createSystemVMAsync(tenant, space) : vm;
  }

  @Override
  public VMachine createTestVMAsync(TenantReference tenant, SpaceReference space, boolean hasUI) throws CreateVMException {
    VMachine vm = vmachineService.getSystemVM(tenant, space);
    return vm == null ? createSystemVMAsync(tenant, space) : vm;
  }

  @Override
  public VMachine start(VMachine machine) throws StartVMException {
    throw new UnsupportedOperationException("Not supported on standalone mode");
  }

  @Override
  public VMachine stop(VMachine machine) throws StopVMException {
    throw new UnsupportedOperationException("Not supported on standalone mode");
  }

  @Override
  public VMachine rebuild(VMachine machine) throws RebuildVMException {
    return null;
  }

  @Override
  public void destroy(VMachine machine) throws DestroyVMException {
    throw new UnsupportedOperationException("Not supported on standalone mode");
  }

  @Override
  public VMachine allocateFloatingIp(VMachine vm) {
    throw new UnsupportedOperationException("Not supported on standalone mode");
  }

  @Override
  public VMachine deallocateFloatingIp(VMachine vm) {
    throw new UnsupportedOperationException("Not supported on standalone mode");
  }

  @Override
  public void addCredential(String tenant, String username, String password) {
    throw new UnsupportedOperationException("Not supported on standalone mode");
  }

  @Override
  public void addCredential(String tenant) {
    throw new UnsupportedOperationException("Not supported on standalone mode");
  }

  @Override
  public boolean isVMReady(VMachine vm) {
    return true;
  }

  @Override
  public VMachine initSystemVM(VMachine vm) throws CreateVMException {
    return vm;
  }

  @Override
  public VMachine initTestVmUI(VMachine vm) throws Exception {
    return vm;
  }

  @Override
  public VMachine initTestVMNonUI(VMachine vm) throws Exception {
    return vm;
  }

}
