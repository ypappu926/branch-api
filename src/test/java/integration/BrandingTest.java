/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package integration;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.views.JobColumn;
import hudson.views.StatusColumn;
import hudson.views.WeatherColumn;
import integration.harness.BasicMultiBranchProject;
import integration.harness.BasicMultiBranchProjectFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.branch.Branch;
import jenkins.branch.BranchIndexingCause;
import jenkins.branch.BranchSource;
import jenkins.branch.DescriptionColumn;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvents;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMLink;
import jenkins.scm.impl.mock.MockSCMNavigator;
import jenkins.scm.impl.mock.MockSCMSource;
import jenkins.scm.impl.mock.MockSCMSourceEvent;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class BrandingTest {

    /**
     * All tests in this class only create items and do not affect other global configuration, thus we trade test
     * execution time for the restriction on only touching items.
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    public void given_multibranch_when_noSourcesDefined_then_noSourceBrandingPresent() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
        }
    }

    @Test
    public void given_multibranch_when_sourceDefined_then_sourceBrandingPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getAction(MockSCMLink.class), hasProperty("id", is("source")));
        }
    }

    @Test
    public void given_multibranch_when_sourceDefined_then_sourceBrandingPresentAfterSourceEvent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            fire(new MockSCMSourceEvent(null, SCMEvent.Type.UPDATED, c, "foo"));
            r.waitUntilNoActivity();
            assertThat(prj.getAction(MockSCMLink.class), hasProperty("id", is("source")));
        }
    }

    @Test
    public void given_multibranch_when_branches_then_branchBrandingPresent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("master").getAction(MockSCMLink.class), hasProperty("id", is("branch")));
        }
    }

    @Test
    public void given_multibranch_when_branches_then_runBrandingPresent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("master").getBuildByNumber(1).getAction(MockSCMLink.class),
                    hasProperty("id", is("revision")));
        }
    }

    @Test
    @Issue("JENKINS-48090")
    public void given_multibranch_when_runBrandingIncludesAdditionalCauses_then_causesMerged() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new BrandedMockSCMSource(c)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            CauseAction causeAction = prj.getItem("master").getBuildByNumber(1).getAction(CauseAction.class);
            assertThat(causeAction.getCauses(), containsInAnyOrder(
                    instanceOf(BranchIndexingCause.class),
                    instanceOf(Cause.UserIdCause.class),
                    instanceOf(Cause.RemoteCause.class)
                    ));
        }
    }

    @Test
    public void given_orgFolder_when_noNavigatorsDefined_then_noNavigatorBrandingPresent() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
        }
    }

    @Test
    public void given_multibranch_when_sourceHasNonSafeNames_then_branchDisplayNameNotMangled() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", ".");
            c.createBranch("foo", "..");
            c.createBranch("foo", "../..");

            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            // now for the fun
            FreeStyleProject master = null;
            FreeStyleProject dot = null;
            FreeStyleProject dotdot = null;
            FreeStyleProject dotdotslashdotdot = null;

            for (FreeStyleProject p : prj.getItems()) {
                Branch branch = prj.getProjectFactory().getBranch(p);
                String name = branch.getName();
                if ("master".equals(name)) {
                    master = p;
                } else if (".".equals(name)) {
                    dot = p;
                } else if ("..".equals(name)) {
                    dotdot = p;
                } else if ("../..".equals(name)) {
                    dotdotslashdotdot = p;
                }
            }
            assertThat("We have the master branch", master, notNullValue());
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch build was success", master.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(master.getDisplayName(), is("master"));
            assertThat(master.getName(), is("master"));

            assertThat("We have the . branch", dot, notNullValue());
            assertThat("The . branch was built", dot.getLastBuild(), notNullValue());
            assertThat("The . branch build was success", dot.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(dot.getDisplayName(), is("."));
            assertThat(dot.getName(), not(is(".")));

            assertThat("We have the .. branch", dotdot, notNullValue());
            assertThat("The .. branch was built", dotdot.getLastBuild(), notNullValue());
            assertThat("The .. branch build was success", dotdot.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(dotdot.getDisplayName(), is(".."));
            assertThat(dotdot.getName(), not(is("..")));

            assertThat("We have the ../.. branch", dotdotslashdotdot, notNullValue());
            assertThat("The ../.. branch was built", dotdotslashdotdot.getLastBuild(), notNullValue());
            assertThat("The ../.. branch build was success", dotdotslashdotdot.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(dotdotslashdotdot.getDisplayName(), is("../.."));
            assertThat(dotdotslashdotdot.getName(), not(is("../..")));

            assertThat(prj.getItems(), containsInAnyOrder(master, dot, dotdot, dotdotslashdotdot));
        }
    }

    @Test
    public void given_multibranch_when_sourceHasI18nNames_then_branchDisplayNameNotMangled() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo","??????/???");
            c.createBranch("foo","??????/?????????");
            c.createBranch("foo","gn??/nua");
            c.createBranch("foo","caracter??stica/nuevo");
            c.createBranch("foo","??????????????????????/??????????");

            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            // now for the fun
            FreeStyleProject master = null;
            FreeStyleProject irish = null;
            FreeStyleProject chinese = null;
            FreeStyleProject korean = null;
            FreeStyleProject spanish = null;
            FreeStyleProject russian = null;

            for (FreeStyleProject p: prj.getItems()) {
                Branch branch = prj.getProjectFactory().getBranch(p);
                String name = branch.getName();
                if ("master".equals(name)) {
                    master = p;
                } else if ("gn??/nua".equals(name)) {
                    irish = p;
                } else if ("??????/???".equals(name)) {
                    chinese = p;
                } else if ("??????/?????????".equals(name)) {
                    korean = p;
                } else if ("caracter??stica/nuevo".equals(name)) {
                    spanish = p;
                } else if ("??????????????????????/??????????".equals(name)) {
                    russian = p;
                }
            }
            assertThat("We have the master branch", master, notNullValue());
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch build was success", master.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(master.getDisplayName(), is("master"));
            assertThat(master.getName(), is("master"));

            assertThat("We have the Irish branch", irish, notNullValue());
            assertThat("The Irish branch was built", irish.getLastBuild(), notNullValue());
            assertThat("The Irish branch build was success", irish.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(irish.getDisplayName(), is("gn??/nua"));
            assertThat(irish.getName(), not(is("gn??/nua")));

            assertThat("We have the Chinese branch", chinese, notNullValue());
            assertThat("The Chinese branch was built", chinese.getLastBuild(), notNullValue());
            assertThat("The Chinese branch build was success", chinese.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(chinese.getDisplayName(), is("??????/???"));
            assertThat(chinese.getName(), not(is("??????/???")));

            assertThat("We have the Korean branch", korean, notNullValue());
            assertThat("The Korean branch was built", korean.getLastBuild(), notNullValue());
            assertThat("The Korean branch build was success", korean.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(korean.getDisplayName(), is("??????/?????????"));
            assertThat(korean.getName(), not(is("??????/?????????")));

            assertThat("We have the Spanish branch", spanish, notNullValue());
            assertThat("The Spanish branch was built", spanish.getLastBuild(), notNullValue());
            assertThat("The Spanish branch build was success", spanish.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(spanish.getDisplayName(), is("caracter??stica/nuevo"));
            assertThat(spanish.getName(), not(is("caracter??stica/nuevo")));

            assertThat("We have the Russian branch", russian, notNullValue());
            assertThat("The Russian branch was built", russian.getLastBuild(), notNullValue());
            assertThat("The Russian branch build was success", russian.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(russian.getDisplayName(), is("??????????????????????/??????????"));
            assertThat(russian.getName(), not(is("??????????????????????/??????????")));

            assertThat(prj.getItems(), containsInAnyOrder(master, irish, chinese, korean, spanish, russian));

            assertThat(prj.getItemByBranchName("master"), is(master));
            assertThat(prj.getItemByBranchName("gn??/nua"), is(irish));
            assertThat(prj.getItemByBranchName("??????????????????????/??????????"), is(russian));
            assertThat(prj.getItemByBranchName("??????/???"), is(chinese));
            assertThat(prj.getItemByBranchName("caracter??stica/nuevo"), is(spanish));
            assertThat(prj.getItemByBranchName("??????/?????????"), is(korean));
        }
    }

    @Test
    public void given_orgFolder_when_navigatorDefined_then_navigatorBrandingPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getAction(MockSCMLink.class), hasProperty("id", is("organization")));
        }
    }

    @Test
    public void given_orgFolderWithI18nRepos_when_indexing_then_repoNamesEncoded()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("England");
            c.createRepository("??ireann");
            c.createRepository("????????????");
            c.createRepository("??????");
            c.createRepository("Espa??a");
            c.createRepository("????????????");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "multicultural");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            MultiBranchProject england = null;
            MultiBranchProject ireland = null;
            MultiBranchProject russia = null;
            MultiBranchProject china = null;
            MultiBranchProject spain = null;
            MultiBranchProject korea = null;

            for (MultiBranchProject p : prj.getItems()) {
                String name = p.getDisplayName();
                if ("England".equals(name)) {
                    england = p;
                } else if ("??ireann".equals(name)) {
                    ireland = p;
                } else if ("????????????".equals(name)) {
                    russia = p;
                } else if ("??????".equals(name)) {
                    china = p;
                } else if ("Espa??a".equals(name)) {
                    spain = p;
                } else if ("????????????".equals(name)) {
                    korea = p;
                }
            }

            assertThat("England", england, notNullValue());
            assertThat("England/master", england.getItem("master"), notNullValue());
            assertThat("England/master/lastBuild", england.getItem("master").getLastBuild(), notNullValue());

            assertThat("Ireland", ireland, notNullValue());
            assertThat("Ireland/master", ireland.getItem("master"), notNullValue());
            assertThat("Ireland/master/lastBuild", ireland.getItem("master").getLastBuild(), notNullValue());

            assertThat("Russia", russia, notNullValue());
            assertThat("Russia/master", russia.getItem("master"), notNullValue());
            assertThat("Russia/master/lastBuild", russia.getItem("master").getLastBuild(), notNullValue());

            assertThat("China", china, notNullValue());
            assertThat("China/master", china.getItem("master"), notNullValue());
            assertThat("China/master/lastBuild", china.getItem("master").getLastBuild(), notNullValue());

            assertThat("Spain", spain, notNullValue());
            assertThat("Spain/master", spain.getItem("master"), notNullValue());
            assertThat("Spain/master/lastBuild", spain.getItem("master").getLastBuild(), notNullValue());

            assertThat("Korea", korea, notNullValue());
            assertThat("Korea/master", korea.getItem("master"), notNullValue());
            assertThat("Korea/master/lastBuild", korea.getItem("master").getLastBuild(), notNullValue());

            assertThat(prj.getItems(), containsInAnyOrder(england, ireland, spain, china, russia, korea));

            assertThat(prj.getItemByProjectName("England"), is(england));
            assertThat(prj.getItemByProjectName("??ireann"), is(ireland));
            assertThat(prj.getItemByProjectName("????????????"), is(russia));
            assertThat(prj.getItemByProjectName("??????"), is(china));
            assertThat(prj.getItemByProjectName("Espa??a"), is(spain));
            assertThat(prj.getItemByProjectName("????????????"), is(korea));
        }
    }

    @Test
    public void given_orgFolderWithNonSafeRepos_when_indexing_then_repoNamesEncoded()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("a?");
            c.createRepository("a*");
            c.createRepository("a/b");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "multicultural");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            MultiBranchProject question = null;
            MultiBranchProject star = null;
            MultiBranchProject slash = null;

            for (MultiBranchProject p : prj.getItems()) {
                String name = p.getDisplayName();
                System.out.println(name);
                if ("a?".equals(name)) {
                    question = p;
                } else if ("a*".equals(name)) {
                    star = p;
                } else if ("a/b".equals(name)) {
                    slash = p;
                }
            }

            assertThat("a?", question, notNullValue());
            assertThat("a?/master", question.getItem("master"), notNullValue());
            assertThat("a?/master/lastBuild", question.getItem("master").getLastBuild(), notNullValue());

            assertThat("a*", star, notNullValue());
            assertThat("a*/master", star.getItem("master"), notNullValue());
            assertThat("a*/master/lastBuild", star.getItem("master").getLastBuild(), notNullValue());

            assertThat("a/b", slash, notNullValue());
            assertThat("a/b/master", slash.getItem("master"), notNullValue());
            assertThat("a/b/master/lastBuild", slash.getItem("master").getLastBuild(), notNullValue());

            assertThat(prj.getItems(), containsInAnyOrder(question, star, slash));
        }
    }

    @Test
    public void given_orgFolder_when_navigatorDefined_then_sourceBrandingPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("foo").getAction(MockSCMLink.class), hasProperty("id", is("source")));
        }
    }

    @Test
    public void given_orgFolder_when_navigatorDefined_then_branchBrandingPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("foo").getItem("master").getAction(MockSCMLink.class),
                    hasProperty("id", is("branch")));
        }
    }

    @Test
    public void given_orgFolder_when_navigatorDefined_then_revisionBrandingPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("foo").getItem("master").getBuildByNumber(1).getAction(MockSCMLink.class),
                    hasProperty("id", is("revision")));
        }
    }

    @Test
    public void given_multibranch_when_decoratedSourceDefined_then_descriptionPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setDescription("foo", "The Foo Project of Manchu");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            assertThat(prj.getDescription(), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getDescription(), is("The Foo Project of Manchu"));
        }
    }

    @Test
    public void given_multibranch_when_decoratedSourceDefined_then_displayNamePresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setDisplayName("foo", "Foo Project");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            assertThat(prj.getDisplayName(), is("foo"));
            assertThat(prj.getDisplayNameOrNull(), nullValue());
            assertThat(prj.getAction(ObjectMetadataAction.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getAction(ObjectMetadataAction.class), notNullValue());
            assertThat(prj.getDisplayName(), is("Foo Project"));
        }
    }

    @Test
    public void given_orgFolder_when_decoratedSourceDefined_then_descriptionLinkPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setDescription("foo", "The Foo Project of Manchu");
            c.setUrl("foo", "http://foo.manchu.example.com/");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("foo").getAction(ObjectMetadataAction.class), allOf(
                    hasProperty("objectDescription", is("The Foo Project of Manchu")),
                    hasProperty("objectUrl", is("http://foo.manchu.example.com/")),
                    hasProperty("objectDisplayName", nullValue())
            ));
            JenkinsRule.WebClient webClient = r.createWebClient();
            HtmlPage page = webClient.getPage(prj);
            HtmlAnchor href = page.getAnchorByHref("http://foo.manchu.example.com/");
            assertThat(href.getTextContent(), containsString("The Foo Project of Manchu"));
        }
    }

    @Test
    public void given_multibranch_when_decoratedSourceDefined_then_folderIconPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setRepoIconClassName("icon-star");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            assertThat(prj.getIcon().getIconClassName(), not(is("icon-star")));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getIcon().getIconClassName(), is("icon-star"));
        }
    }

    @Test
    public void given_orgFolder_when_decoratedOrganizationDefined_then_folderIconPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setOrgIconClassName("icon-star");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getIcon().getIconClassName(), not(is("icon-star")));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getIcon().getIconClassName(), is("icon-star"));
        }
    }

    @Test
    public void given_orgFolder_when_decoratedOrganizationDefined_then_displayNamePresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setDisplayName("Foo Organization");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getDisplayName(), is("foo"));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getDisplayName(), is("Foo Organization"));
        }
    }

    @Test
    public void given_orgFolder_when_decoratedOrganizationDefined_then_descriptionLinkPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setDescription("The Foo of Manchu");
            c.setUrl("http://foo.manchu.example.com/");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getAction(ObjectMetadataAction.class), allOf(
                    hasProperty("objectDescription", is("The Foo of Manchu")),
                    hasProperty("objectUrl", is("http://foo.manchu.example.com/")),
                    hasProperty("objectDisplayName", nullValue())
            ));
            ListView view = new ListView("descript");
            view.setIncludeRegex(".*");
            view.getColumns().replaceBy(Arrays.asList(
                    new StatusColumn(),
                    new WeatherColumn(),
                    new JobColumn(),
                    new DescriptionColumn()
            ));
            r.jenkins.addView(view);
            JenkinsRule.WebClient webClient = r.createWebClient();
            HtmlPage page = webClient.getPage(view);
            HtmlAnchor href = page.getAnchorByHref("http://foo.manchu.example.com/");
            assertThat(href.getTextContent(), containsString("The Foo of Manchu"));
        }
    }

    private void fire(MockSCMSourceEvent event) throws Exception {
        long watermark = SCMEvents.getWatermark();
        SCMSourceEvent.fireNow(event);
        SCMEvents.awaitAll(watermark);
        r.waitUntilNoActivity();
    }

    public static class BrandedMockSCMSource extends MockSCMSource {
        public BrandedMockSCMSource(MockSCMController c) {
            super(c, "foo", new MockSCMDiscoverBranches());
        }

        @NonNull
        @Override
        protected List<Action> retrieveActions(@NonNull SCMRevision revision, SCMHeadEvent event,
                                               @NonNull TaskListener listener)
                throws IOException, InterruptedException {
            List<Action> result = new ArrayList<>(super.retrieveActions(revision, event, listener));
            result.add(new CauseAction(new Cause.UserIdCause()));
            result.add(new CauseAction(new Cause.RemoteCause("test", "data")));
            return result;
        }

        @Extension()
        public static class DescriptorImpl extends MockSCMSource.DescriptorImpl {}
    }
}
