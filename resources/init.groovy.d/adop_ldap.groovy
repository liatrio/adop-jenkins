import hudson.model.*;
import jenkins.model.*;
import hudson.security.*;
import jenkins.security.plugins.ldap.*;
import hudson.util.Secret;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cloudbees.plugins.credentials.CredentialsScope;
import javaposse.jobdsl.plugin.GlobalJobDslSecurityConfiguration

// Check if enabled
def env = System.getenv()
if (!env['ADOP_LDAP_ENABLED'].toBoolean()) {
    println "--> ADOP LDAP Disabled"
    return
}

// Variables
def ldap_server = env['LDAP_SERVER']
def ldap_rootDN = env['LDAP_ROOTDN']
def ldap_userSearchBase = env['LDAP_USER_SEARCH_BASE']
def ldap_userSearch = env['LDAP_USER_SEARCH']
def ldap_groupSearchBase = env['LDAP_GROUP_SEARCH_BASE']
def ldap_groupSearchFilter = env['LDAP_GROUP_SEARCH_FILTER']
def ldap_groupMembershipFilter = env['LDAP_GROUP_MEMBERSHIP_FILTER']
def ldap_managerDN = env['LDAP_MANAGER_DN']
def ldap_managerPassword = env['LDAP_MANAGER_PASSWORD']
def ldap_displayNameAttributeName = env['LDAP_DISPLAY_NAME_ATTRIBUTE_NAME']
def ldap_mailAddressAttributeName = env['LDAP_MAIL_ADDRESS_ATTRIBUTE_NAME']

def ldap_inhibitInferRootDN = false
def ldap_disableMailAddressResolver = false


// Constants
def instance = Jenkins.getInstance()

Thread.start {
    sleep 10000

    // Add Global credentials for LDAP
    println "--> Registering LDAP Credentials"
    def system_credentials_provider = SystemCredentialsProvider.getInstance()

    def credential_description = "ADOP LDAP Admin"

    ldap_credentials_exist = false
    system_credentials_provider.getCredentials().each {
        credentials = (com.cloudbees.plugins.credentials.Credentials) it
        if ( credentials.getDescription() == credential_description) {
            ldap_credentials_exist = true
            println("Found existing credentials: " + credential_description)
        }
    }

    if(!ldap_credentials_exist) {
        def credential_scope = CredentialsScope.GLOBAL
        def credential_id = "adop-ldap-admin"
        def credential_username = ldap_managerDN
        def credential_password = ldap_managerPassword

        def credential_domain = com.cloudbees.plugins.credentials.domains.Domain.global()
        def credential_creds = new UsernamePasswordCredentialsImpl(credential_scope,credential_id,credential_description,credential_username,credential_password)

        system_credentials_provider.addCredentials(credential_domain,credential_creds)
    }

    // LDAP
    println "--> Configuring LDAP"

    def ldapRealm = new LDAPSecurityRealm(
        ldap_server, //String server
        ldap_rootDN, //String rootDN
        ldap_userSearchBase, //String userSearchBase
        ldap_userSearch, //String userSearch
        ldap_groupSearchBase, //String groupSearchBase
        ldap_groupSearchFilter, //String groupSearchFilter
        new FromGroupSearchLDAPGroupMembershipStrategy(ldap_groupMembershipFilter), //LDAPGroupMembershipStrategy groupMembershipStrategy
        ldap_managerDN, //String managerDN
        Secret.fromString(ldap_managerPassword), //Secret managerPasswordSecret
        ldap_inhibitInferRootDN, //boolean inhibitInferRootDN
        ldap_disableMailAddressResolver, //boolean disableMailAddressResolver
        null, //CacheConfiguration cache
        null, //EnvironmentProperty[] environmentProperties
        ldap_displayNameAttributeName, //String displayNameAttributeName
        ldap_mailAddressAttributeName, //String mailAddressAttributeName
        IdStrategy.CASE_INSENSITIVE, //IdStrategy userIdStrategy
        IdStrategy.CASE_INSENSITIVE //IdStrategy groupIdStrategy >> defaults
    )

    instance.setSecurityRealm(ldapRealm)


    // If no authorisation strategy is in place, setup Admin permissions via matrix-auth
    def authStrategy = Hudson.instance.getAuthorizationStrategy()

    if (authStrategy instanceof AuthorizationStrategy.Unsecured) {

      //specify project matrix-auth
      def strategy = new hudson.security.ProjectMatrixAuthorizationStrategy()

      //get the current admin user information from environment
      def username = System.getenv("INITIAL_ADMIN_USER")
      def password = System.getenv("INITIAL_ADMIN_PASSWORD")

      /* Set permissions for Jenkins */

      //Overall
      strategy.add(Jenkins.ADMINISTER, username)
      strategy.add(Jenkins.RUN_SCRIPTS, username)
      strategy.add(Jenkins.READ, username)

      //Anon permissions (readonly)
      strategy.add(Jenkins.READ, 'anonymous')
      strategy.add(hudson.model.Item.READ, 'anonymous')
      strategy.add(hudson.model.View.READ, 'anonymous')

      //Jobs
      strategy.add(hudson.model.Item.BUILD, username)
      strategy.add(hudson.model.Item.CANCEL, username)
      strategy.add(hudson.model.Item.CONFIGURE, username)
      strategy.add(hudson.model.Item.CREATE, username)
      strategy.add(hudson.model.Item.DELETE, username)
      strategy.add(hudson.model.Item.DISCOVER, username)
      strategy.add(hudson.model.Item.EXTENDED_READ, username)
      strategy.add(hudson.model.Item.READ, username)
      strategy.add(hudson.model.Item.WIPEOUT, username)
      strategy.add(hudson.model.Item.WORKSPACE, username)

      //View
      strategy.add(hudson.model.View.CONFIGURE, username)
      strategy.add(hudson.model.View.CREATE, username)
      strategy.add(hudson.model.View.DELETE, username)
      strategy.add(hudson.model.View.READ, username)

      //Run
      strategy.add(hudson.model.Run.DELETE, username)
      strategy.add(hudson.model.Run.UPDATE, username)
      strategy.add(hudson.model.Run.ARTIFACTS, username)

      //SCM
      strategy.add(hudson.scm.SCM.TAG, username)

      //Manage Plugins
      strategy.add(hudson.PluginManager.UPLOAD_PLUGINS, username)
      strategy.add(hudson.PluginManager.CONFIGURE_UPDATECENTER, username)

      //set strategy
      instance.setAuthorizationStrategy(strategy)
      GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).useScriptSecurity=false
    }

    // Save the state
    instance.save()
}
