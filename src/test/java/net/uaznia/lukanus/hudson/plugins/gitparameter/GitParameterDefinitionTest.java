package net.uaznia.lukanus.hudson.plugins.gitparameter;

import hudson.EnvVars;
import hudson.cli.CLICommand;
import hudson.cli.ConsoleCommand;
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.templateproject.ProxySCM;
import hudson.scm.SCM;
import hudson.tasks.Shell;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import net.sf.json.JSONObject;
import net.uaznia.lukanus.hudson.plugins.gitparameter.GitParameterDefinition.DescriptorImpl;
import net.uaznia.lukanus.hudson.plugins.gitparameter.jobs.JobWrapper;
import net.uaznia.lukanus.hudson.plugins.gitparameter.jobs.JobWrapperFactory;
import net.uaznia.lukanus.hudson.plugins.gitparameter.model.ItemsErrorModel;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static net.uaznia.lukanus.hudson.plugins.gitparameter.Constants.*;
import static net.uaznia.lukanus.hudson.plugins.gitparameter.scms.SCMFactory.getGitSCMs;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author lukanus
 */
public class GitParameterDefinitionTest {
    private FreeStyleProject project;

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    // Test Descriptor.getProjectSCM()
    @Test
    public void testGetProjectSCM() throws Exception {
        FreeStyleProject testJob = jenkins.createFreeStyleProject("testGetProjectSCM");
        GitSCM git = new GitSCM(GIT_PARAMETER_REPOSITORY_URL);
        GitParameterDefinition def = new GitParameterDefinition("testName",
                PT_REVISION,
                "testDefaultValue",
                "testDescription",
                "testBranch",
                ".*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);
        testJob.addProperty(new ParametersDefinitionProperty(def));
        JobWrapper IJobWrapper = JobWrapperFactory.createJobWrapper(testJob);
        assertTrue(getGitSCMs(IJobWrapper, null).isEmpty());

        testJob.setScm(git);
        assertTrue(git.equals(getGitSCMs(IJobWrapper, null).get(0)));
    }
    
    @Test
    public void testDoFillValueItems_withoutSCM() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("testListTags");
        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_TAG",
                "testDefaultValue",
                "testDescription",
                "testBranch",
                ".*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));

        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertEquals(3, items.getErrors().size());
        assertEquals("The default value has been returned", items.getErrors().get(0));
        assertEquals("No Git repository configured in SCM configuration or plugin is configured wrong", items.getErrors().get(1));
        assertEquals("Please check the configuration", items.getErrors().get(2));
        assertTrue(isListBoxItem(items, def.getDefaultValue()));
    }

    @Test
    public void testDoFillValueItems_listTags() throws Exception {
        project = jenkins.createFreeStyleProject("testListTags");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();

        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_TAG",
                "testDefaultValue",
                "testDescription",
                "testBranch",
                ".*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));

        // Run the build once to get the workspace
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "git-parameter-0.2 3f2e96a 2012-02-21 03:58 Łukasz Miłkowski <lukanus@uaznia.net> [maven-release-plugin] prepare release git-parameter-0.2"));
    }

    @Test
    public void testGetListBranchNoBuildProject() throws Exception {
        project = jenkins.createFreeStyleProject("testListTags");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();

        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_BRANCH",
                "testDefaultValue",
                "testDescription",
                "testBranch",
                ".*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));

        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "master"));
    }

    @Test
    public void testGetListBranchAfterWipeOutWorkspace() throws Exception {
        project = jenkins.createFreeStyleProject("testListTags");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();

        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_BRANCH",
                null,
                "testDescription",
                "testBranch",
                ".*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        // build.run();
        project.doDoWipeOutWorkspace();
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "master"));
    }

    @Test
    public void testDoFillValueItems_listBranches() throws Exception {
        project = jenkins.createFreeStyleProject("testListTags");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();

        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_BRANCH",
                "testDefaultValue",
                "testDescription",
                "testBranch",
                ".*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));

        // Run the build once to get the workspace
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "master"));
    }

    @Test
    public void tesListBranchesWrongBranchFilter() throws Exception {
        project = jenkins.createFreeStyleProject("testListTags");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();

        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_BRANCH",
                "testDefaultValue",
                "testDescription",
                "testBranch",
                "[*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));

        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "master"));
    }

    @Test
    public void testDoFillValueItems_listBranches_withRegexGroup() throws Exception {
        project = jenkins.createFreeStyleProject("testListTags");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();

        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_BRANCH",
                "testDefaultValue",
                "testDescription",
                "testBranch",
                "origin/(.*)",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));

        // Run the build once to get the workspace
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "master"));
        assertFalse(isListBoxItem(items, "origin/master"));
    }

    @Test
    public void testSortAscendingTag() throws Exception {
        project = jenkins.createFreeStyleProject("testListTags");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();

        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_TAG",
                "testDefaultValue",
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));

        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        String expected = "0.1 be702bf 2011-10-31 23:05 Łukasz Miłkowski <lukanus@uaznia.net> [maven-release-plugin] prepare release 0.1";
        assertEquals(expected, items.get(0).value);
    }

    @Test
    public void testWrongTagFilter() throws Exception {
        project = jenkins.createFreeStyleProject("testListTags");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();

        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_TAG",
                "testDefaultValue",
                "testDescription",
                null,
                ".*",
                "wrongTagFilter",
                SortMode.ASCENDING, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));

        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(items.isEmpty());
    }

    @Test
    public void testSortDescendingTag() throws Exception {
        project = jenkins.createFreeStyleProject("testListTags");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();

        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_TAG",
                "testDefaultValue",
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.DESCENDING, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));

        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        String expected = "0.1 be702bf 2011-10-31 23:05 Łukasz Miłkowski <lukanus@uaznia.net> [maven-release-plugin] prepare release 0.1";
        assertEquals(expected, items.get(items.size() - 1).value);
    }

    @Test
    public void testDoFillValueItems_listTagsAndBranches() throws Exception {
        project = jenkins.createFreeStyleProject("testListTags");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();

        GitParameterDefinition def = new GitParameterDefinition("testName",
                "PT_BRANCH_TAG",
                "testDefaultValue",
                "testDescription",
                "testBranch",
                ".*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);
        project.addProperty(new ParametersDefinitionProperty(def));

        // Run the build once to get the workspace
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "git-parameter-0.2 3f2e96a 2012-02-21 03:58 Łukasz Miłkowski <lukanus@uaznia.net> [maven-release-plugin] prepare release git-parameter-0.2"));
    }

    @Test
    public void testDoFillValueItems_listRevisionsWithBranch() throws Exception {
        project = jenkins.createFreeStyleProject("testListRevisions");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();
        GitParameterDefinition def = new GitParameterDefinition("testName",
                PT_REVISION,
                "testDefaultValue",
                "testDescription",
                "origin/preview_0_3",
                ".*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);

        project.addProperty(new ParametersDefinitionProperty(def));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "00a8385c"));
    }

    @Test
    public void testDoFillValueItems_listPullRequests() throws Exception {
        project = jenkins.createFreeStyleProject("testListPullRequests");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();
        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_PULL_REQUEST,
                "master",
                "testDescription",
                "",
                ".*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);

        project.addProperty(new ParametersDefinitionProperty(def));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "41"));
        assertTrue(isListBoxItem(items, "44"));
    }

    @Test
    public void testSearchInFolders() throws Exception {
        MockFolder folder = jenkins.createFolder("folder");
        FreeStyleProject job1 = folder.createProject(FreeStyleProject.class, "job1");

        GitParameterDefinition gitParameterDefinition = new GitParameterDefinition(NAME,
                "asdf",
                "other",
                "description",
                "branch",
                ".*",
                "*",
                SortMode.NONE, SelectedValue.NONE, null, false);
        job1.addProperty(new ParametersDefinitionProperty(gitParameterDefinition));
        assertEquals("folder/job1", gitParameterDefinition.getParentJob().getFullName());
    }

    @Test
    public void testBranchFilterValidation() {
        final DescriptorImpl descriptor = new DescriptorImpl();
        final FormValidation okWildcard = descriptor.doCheckBranchFilter(".*");
        final FormValidation badWildcard = descriptor.doCheckBranchFilter(".**");

        assertTrue(okWildcard.kind == Kind.OK);
        assertTrue(badWildcard.kind == Kind.ERROR);
    }

    @Test
    public void testUseRepositoryValidation() {
        final DescriptorImpl descriptor = new DescriptorImpl();
        final FormValidation okWildcard = descriptor.doCheckUseRepository(".*");
        final FormValidation badWildcard = descriptor.doCheckUseRepository(".**");

        assertTrue(okWildcard.kind == Kind.OK);
        assertTrue(badWildcard.kind == Kind.ERROR);
    }

    @Test
    public void testDefaultValueIsRequired() {
        final DescriptorImpl descriptor = new DescriptorImpl();
        final FormValidation okDefaultValue = descriptor.doCheckDefaultValue("origin/master", false);
        final FormValidation badDefaultValue = descriptor.doCheckDefaultValue(null, false);
        final FormValidation badDefaultValue_2 = descriptor.doCheckDefaultValue("  ", false);

        assertTrue(okDefaultValue.kind == Kind.OK);
        assertTrue(badDefaultValue.kind == Kind.WARNING);
        assertTrue(badDefaultValue_2.kind == Kind.WARNING);
    }

    @Test
    public void testDefaultValueIsNotRequired() {
        final DescriptorImpl descriptor = new DescriptorImpl();
        final FormValidation okDefaultValue = descriptor.doCheckDefaultValue("origin/master", true);
        final FormValidation badDefaultValue = descriptor.doCheckDefaultValue(null, true);
        final FormValidation badDefaultValue_2 = descriptor.doCheckDefaultValue("  ", true);

        assertTrue(okDefaultValue.kind == Kind.WARNING);
        assertTrue(badDefaultValue.kind == Kind.OK);
        assertTrue(badDefaultValue_2.kind == Kind.OK);
    }

    @Test
    public void testGetDefaultValueWhenDefaultValueIsSet() throws Exception {
        project = jenkins.createFreeStyleProject("testDefaultValue");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();
        String testDefaultValue = "testDefaultValue";
        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_TAG,
                testDefaultValue,
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.TOP, null, false);

        project.addProperty(new ParametersDefinitionProperty(def));

        assertTrue(testDefaultValue.equals(def.getDefaultParameterValue().getValue()));
    }

    @Test
    public void testGetDefaultValueAsTop() throws Exception {
        project = jenkins.createFreeStyleProject("testDefaultValueAsTOP");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit();
        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_TAG,
                null,
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.TOP, null, false);

        project.addProperty(new ParametersDefinitionProperty(def));

        String expected = "0.1 be702bf 2011-10-31 23:05 Łukasz Miłkowski <lukanus@uaznia.net> [maven-release-plugin] prepare release 0.1";
        assertEquals(expected, def.getDefaultParameterValue().getValue());
    }

    @Test
    public void testGlobalVariableRepositoryUrl() throws Exception {
        EnvVars.masterEnvVars.put("GIT_REPO_URL", GIT_PARAMETER_REPOSITORY_URL);
        project = jenkins.createFreeStyleProject("testGlobalValue");
        project.getBuildersList().add(new Shell("echo test"));
        setupGit("$GIT_REPO_URL");
        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*master.*",
                "*",
                SortMode.ASCENDING, SelectedValue.NONE, null, false);

        project.addProperty(new ParametersDefinitionProperty(def));
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(build.getResult(), Result.SUCCESS);
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "origin/master"));
    }

    @Test(expected = Failure.class)
    public void testRequiredParameterStaplerFail() throws Exception {
        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*master.*",
                "*",
                SortMode.ASCENDING, SelectedValue.NONE, null, false);
        def.setRequiredParameter(true);
        StaplerRequest request = mock(StaplerRequest.class);
        String[] result = new String[]{""};
        when(request.getParameterValues("testName")).thenReturn(result);
        def.createValue(request);
    }

    @Test
    public void testRequiredParameterStaplerPass() throws Exception {
        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*master.*",
                "*",
                SortMode.ASCENDING, SelectedValue.NONE, null, false);
        def.setRequiredParameter(true);
        StaplerRequest request = mock(StaplerRequest.class);
        String[] result = new String[]{"master"};
        when(request.getParameterValues("testName")).thenReturn(result);
        def.createValue(request);
    }

    @Test(expected = Failure.class)
    public void testRequiredParameterJSONFail() throws Exception {
        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*master.*",
                "*",
                SortMode.ASCENDING, SelectedValue.NONE, null, false);
        def.setRequiredParameter(true);
        JSONObject o = new JSONObject();
        o.put("value", "");
        def.createValue(null, o);
    }

    @Test
    public void testRequiredParameterJSONPass() throws Exception {
        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*master.*",
                "*",
                SortMode.ASCENDING, SelectedValue.NONE, null, false);
        def.setRequiredParameter(true);
        JSONObject o = new JSONObject();
        o.put("value", "master");
        o.put("name", "testName");
        def.createValue(null, o);
    }

    @Test
    public void testWorkflowJobWithCpsScmFlowDefinition() throws IOException, InterruptedException {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class, "wfj");
        p.setDefinition(new CpsScmFlowDefinition(getGitSCM(), "jenkinsfile"));

        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_TAG,
                null,
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.TOP, null, false);

        p.addProperty(new ParametersDefinitionProperty(def));
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(p, def.getName());
        assertTrue(isListBoxItem(items, "git-parameter-0.2"));
    }

    @Test
    public void testWorkflowJobWithCpsFlowDefinition() throws IOException, InterruptedException, ExecutionException {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class, "wfj");
        String script = "node {\n" +
                " git url: '" + GIT_PARAMETER_REPOSITORY_URL + "' \n" +
                " echo 'Some message'\n" +
                "}";


        p.setDefinition(new CpsFlowDefinition(script, false));
        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_TAG,
                null,
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.TOP, null, false);

        p.addProperty(new ParametersDefinitionProperty(def));
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(p, def.getName());
        //First build is fake build! And should return no Repository configured
        assertEquals(((ItemsErrorModel) items).getErrors().get(1), Messages.GitParameterDefinition_noRepositoryConfigured());

        p.scheduleBuild2(0).get();
        items = def.getDescriptor().doFillValueItems(p, def.getName());
        assertTrue(isListBoxItem(items, "git-parameter-0.2"));
    }

    @Test
    public void testProxySCM() throws IOException, InterruptedException {
        FreeStyleProject anotherProject = jenkins.createFreeStyleProject("AnotherProject");
        anotherProject.getBuildersList().add(new Shell("echo test"));
        anotherProject.setScm(getGitSCM());

        project = jenkins.createFreeStyleProject("projectHaveProxySCM");
        project.setScm(new ProxySCM("AnotherProject"));

        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_TAG,
                null,
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.TOP, null, false);

        project.addProperty(new ParametersDefinitionProperty(def));
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "git-parameter-0.2"));
    }

    @Test
    public void testParameterDefinedRepositoryUrl() throws Exception {
        project = jenkins.createFreeStyleProject("testLocalValue");
        project.getBuildersList().add(new Shell("echo test"));

        StringParameterDefinition stringParameterDef = new StringParameterDefinition("GIT_REPO_URL", GIT_PARAMETER_REPOSITORY_URL, "Description");
        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*master.*",
                "*",
                SortMode.ASCENDING, SelectedValue.NONE, null, false);

        ParametersDefinitionProperty jobProp = new ParametersDefinitionProperty(stringParameterDef, def);
        project.addProperty(jobProp);
        setupGit("${GIT_REPO_URL}");

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(build.getResult(), Result.SUCCESS);
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "origin/master"));
    }

    @Test
    public void testMultiRepositoryInOneSCM() throws IOException, InterruptedException {
        project = jenkins.createFreeStyleProject("projectHaveMultiRepositoryInOneSCM");
        project.getBuildersList().add(new Shell("echo test"));
        SCM gitSCM = getGitSCM(EXAMPLE_REPOSITORY_A_URL, EXAMPLE_REPOSITORY_B_URL);
        project.setScm(gitSCM);

        GitParameterDefinition def = new GitParameterDefinition("name_git_parameter",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.TOP, null, false);


        project.addProperty(new ParametersDefinitionProperty(def));
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "exA_branch_1"));
        assertFalse(isListBoxItem(items, "exB_branch_1"));

        def.setUseRepository(".*exampleB.git");
        items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertFalse(isListBoxItem(items, "exA_branch_1"));
        assertTrue(isListBoxItem(items, "exB_branch_1"));
    }

    @Test
    public void testMultiSCM() throws IOException, InterruptedException {
        project = jenkins.createFreeStyleProject("projectHaveMultiSCM");
        project.getBuildersList().add(new Shell("echo test"));
        MultiSCM multiSCM = new MultiSCM(Arrays.asList(getGitSCM(EXAMPLE_REPOSITORY_A_URL), getGitSCM(EXAMPLE_REPOSITORY_B_URL)));
        project.setScm(multiSCM);

        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.TOP, null, false);

        project.addProperty(new ParametersDefinitionProperty(def));
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "origin/exA_branch_1"));
        assertFalse(isListBoxItem(items, "origin/exB_branch_1"));

        def.setUseRepository(".*exampleB.git");
        items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertFalse(isListBoxItem(items, "origin/exA_branch_1"));
        assertTrue(isListBoxItem(items, "origin/exB_branch_1"));
    }

    @Test
    public void testMultiSCM_forSubdirectoryForRepo() throws IOException, InterruptedException {
        project = jenkins.createFreeStyleProject("projectHaveMultiSCM");
        project.getBuildersList().add(new Shell("echo test"));
        GitSCM gitSCM = (GitSCM) getGitSCM(GIT_CLIENT_REPOSITORY_URL);
        gitSCM.getExtensions().add(new RelativeTargetDirectory("subDirectory"));
        MultiSCM multiSCM = new MultiSCM(Arrays.asList(getGitSCM(), gitSCM));
        project.setScm(multiSCM);

        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.TOP, null, false);

        project.addProperty(new ParametersDefinitionProperty(def));
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "origin/master"));

        def.setUseRepository(".*git-client-plugin.git");
        items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "origin/master"));
    }

    @Test
    public void testMultiSCM_forSubdirectoryForTheSomeRepo() throws IOException, InterruptedException {
        project = jenkins.createFreeStyleProject("projectHaveMultiSCM");
        project.getBuildersList().add(new Shell("echo test"));
        GitSCM gitSCM = (GitSCM) getGitSCM(GIT_CLIENT_REPOSITORY_URL);
        gitSCM.getExtensions().add(new RelativeTargetDirectory("subDirectory"));
        MultiSCM multiSCM = new MultiSCM(Arrays.asList(getGitSCM(GIT_CLIENT_REPOSITORY_URL), gitSCM));
        project.setScm(multiSCM);

        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.TOP, null, false);

        project.addProperty(new ParametersDefinitionProperty(def));
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "origin/master"));
        int expected = items.size();

        def.setUseRepository(".*git-client-plugin.git");
        items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "origin/master"));
        assertEquals(expected, items.size());
    }

    @Test
    public void testMultiSCM_repositoryUrlIsNotSet() throws IOException, InterruptedException {
        project = jenkins.createFreeStyleProject("projectHaveMultiSCM");
        project.getBuildersList().add(new Shell("echo test"));
        GitSCM gitSCM = (GitSCM) getGitSCM(GIT_CLIENT_REPOSITORY_URL);
        gitSCM.getExtensions().add(new RelativeTargetDirectory("subDirectory"));
        MultiSCM multiSCM = new MultiSCM(Arrays.asList(getGitSCM(""), gitSCM));
        project.setScm(multiSCM);

        GitParameterDefinition def = new GitParameterDefinition("testName",
                Consts.PARAMETER_TYPE_BRANCH,
                null,
                "testDescription",
                null,
                ".*",
                "*",
                SortMode.ASCENDING, SelectedValue.TOP, null, false);
        def.setUseRepository(".*git-client-plugin.git");

        project.addProperty(new ParametersDefinitionProperty(def));
        ItemsErrorModel items = def.getDescriptor().doFillValueItems(project, def.getName());
        assertTrue(isListBoxItem(items, "origin/master"));
    }

    @Test
    public void symbolPipelineTest() {
        Descriptor<? extends Describable> gitParameter = SymbolLookup.get().findDescriptor(Describable.class, "gitParameter");
        assertNotNull(gitParameter);
    }

    @Test
    public void testCreateValue_CLICommand() throws IOException, InterruptedException {
        CLICommand cliCommand = new ConsoleCommand();
        GitParameterDefinition instance = new GitParameterDefinition(NAME, PT_REVISION, DEFAULT_VALUE, "description", "branch", ".*", "*", SortMode.NONE, SelectedValue.NONE, null, false);

        String value = "test";
        ParameterValue result = instance.createValue(cliCommand, value);
        assertEquals(result, new GitParameterValue(NAME, value));
    }

    @Test(expected = Failure.class)
    public void testCreateRequiredValueFail_CLICommand() throws IOException, InterruptedException {
        CLICommand cliCommand = new ConsoleCommand();
        GitParameterDefinition instance = new GitParameterDefinition(NAME, PT_REVISION, "", "description", "branch", ".*", "*", SortMode.NONE, SelectedValue.NONE, null, false);
        instance.setRequiredParameter(true);
        instance.createValue(cliCommand, "");
    }

    @Test
    public void testCreateRequiredValuePass_CLICommand() throws IOException, InterruptedException {
        CLICommand cliCommand = new ConsoleCommand();
        GitParameterDefinition instance = new GitParameterDefinition(NAME, PT_REVISION, DEFAULT_VALUE, "description", "branch", ".*", "*", SortMode.NONE, SelectedValue.NONE, null, false);
        instance.setRequiredParameter(true);
        String value = "test";
        ParameterValue result = instance.createValue(cliCommand, value);
        assertEquals(result, new GitParameterValue(NAME, value));
    }

    @Test
    public void testCreateValue_CLICommand_EmptyValue() throws IOException, InterruptedException {
        CLICommand cliCommand = new ConsoleCommand();
        GitParameterDefinition instance = new GitParameterDefinition(NAME, PT_REVISION, DEFAULT_VALUE, "description", "branch", ".*", "*", SortMode.NONE, SelectedValue.NONE, null, false);

        ParameterValue result = instance.createValue(cliCommand, null);
        assertEquals(result, new GitParameterValue(NAME, DEFAULT_VALUE));
    }

    private void setupGit() throws IOException {
        setupGit(GIT_PARAMETER_REPOSITORY_URL);
    }

    private void setupGit(String url) throws IOException {
        SCM git = getGitSCM(url);
        project.setScm(git);
    }

    private SCM getGitSCM() {
        return getGitSCM(GIT_PARAMETER_REPOSITORY_URL);
    }

    private SCM getGitSCM(String... urls) {
        List<UserRemoteConfig> configs = new ArrayList<UserRemoteConfig>();
        for (String url : urls) {
            UserRemoteConfig config = new UserRemoteConfig(url, null, null, null);
            configs.add(config);
        }
        return new GitSCM(configs, null, false, null, null, null, null);
    }

    private boolean isListBoxItem(ItemsErrorModel items, String item) {
        boolean itemExists = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).value.contains(item)) {
                itemExists = true;
            }
        }
        return itemExists;
    }

    private boolean isListBoxItemName(ItemsErrorModel items, String item) {
        boolean itemExists = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).name.contains(item)) {
                itemExists = true;
            }
        }
        return itemExists;
    }
}
