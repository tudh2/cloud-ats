/**
 * 
 */
package org.ats.services.iaas;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.ats.services.organization.entity.fatory.ReferenceFactory;
import org.ats.services.vmachine.VMachineFactory;
import org.ats.services.vmachine.VMachineReference;
import org.ats.services.vmachine.VMachineService;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;

/**
 * @author <a href="mailto:haithanh0809@gmail.com">Nguyen Thanh Hai</a>
 *
 * Jul 2, 2015
 */
public class VMachineServiceModule extends AbstractModule {
  
  /** .*/
  public static final String VM_CONF = "ats.cloud.vm.conf";
  
  /** .*/
  private Properties configuration;
  
  public VMachineServiceModule(String configPath) throws FileNotFoundException, IOException {
    Properties configuration = new Properties();
    if (configPath != null && !configPath.isEmpty()) configuration.load(new FileInputStream(configPath));
    this.configuration = configuration;
  }
  
  public void setProperty(String name, String value) {
    this.configuration.put(name, value);
  }
  
  @Override
  protected void configure() {
    Names.bindProperties(binder(), this.configuration);

    bind(VMachineService.class);
    bind(StandaloneService.class);
    bind(IaaSService.class).toProvider(IaaSServiceProvider.class);
    
    install(new FactoryModuleBuilder().build(VMachineFactory.class));
    install(new FactoryModuleBuilder().build(new TypeLiteral<ReferenceFactory<VMachineReference>>(){}));
    
  }

}
