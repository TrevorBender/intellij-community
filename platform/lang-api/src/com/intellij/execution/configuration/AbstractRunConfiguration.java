package com.intellij.execution.configuration;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author traff
 */
public abstract class AbstractRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> {
  private Map<String, String> myEnvs = new LinkedHashMap<String, String>();
  private boolean myPassParentEnvs = true;

  public AbstractRunConfiguration(String name, RunConfigurationModule configurationModule, ConfigurationFactory factory) {
    super(name, configurationModule, factory);
  }

  public Map<String, String> getEnvs() {
    return myEnvs;
  }

  public void setEnvs(final Map<String, String> envs) {
    myEnvs = envs;
  }

  public boolean isPassParentEnvs() {
    return myPassParentEnvs;
  }

  public void setPassParentEnvs(final boolean passParentEnvs) {
    myPassParentEnvs = passParentEnvs;
  }
}
