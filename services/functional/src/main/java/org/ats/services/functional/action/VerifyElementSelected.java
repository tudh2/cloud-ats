/**
 * 
 */
package org.ats.services.functional.action;

import java.io.IOException;

import org.ats.common.MapBuilder;
import org.ats.services.functional.locator.ILocator;
import org.rythmengine.RythmEngine;

/**
 * @author TrinhTV3
 *
 * Email: TrinhTV3@fsoft.com.vn
 */
public class VerifyElementSelected implements IAction {

  private ILocator locator;
  
  private boolean negated;
  
  /**
   * 
   */
  public VerifyElementSelected(ILocator locator, boolean negated) {

    this.locator = locator;
    this.negated = negated;
  }
  public String transform() throws IOException {
    
    StringBuilder sb = new StringBuilder("if (").append(negated ? "" : "!");
    sb.append("(wd.findElement(@locator).isSelected())) {\n");
    sb.append("      System.out.println(\"");
    sb.append(negated ? "!" : "").append("verifyElementSelected failed\");\n");
    sb.append("    }\n");
    
    RythmEngine engine = new RythmEngine(new MapBuilder<String, Boolean>("codegen.compact", false).build());
    return engine.render(sb.toString(), locator.transform());
  }

  public String getAction() {
    return "verifyElementSelected";
  }

}
