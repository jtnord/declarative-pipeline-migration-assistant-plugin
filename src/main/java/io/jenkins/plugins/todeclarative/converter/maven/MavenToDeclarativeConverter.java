package io.jenkins.plugins.todeclarative.converter.maven;

import hudson.Extension;
import hudson.maven.MavenModuleSet;
import hudson.model.Job;
import hudson.model.Label;
import hudson.tasks.Builder;
import io.jenkins.plugins.todeclarative.converter.ConverterRequest;
import io.jenkins.plugins.todeclarative.converter.ConverterResult;
import io.jenkins.plugins.todeclarative.converter.ToDeclarativeConverter;
import io.jenkins.plugins.todeclarative.converter.builders.BuilderConverter;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTKey;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNamedArgumentList;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTSingleArgument;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTreeStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Extension
public class MavenToDeclarativeConverter
    implements ToDeclarativeConverter
{

    public void convert( ConverterRequest request , ConverterResult converterResult)
    {
        if(! (request.getJob() instanceof MavenModuleSet))
        {
            // TODO log something
        }

        MavenModuleSet mavenModuleSet = (MavenModuleSet) request.getJob();

        ModelASTPipelineDef pipelineDef = converterResult.getModelASTPipelineDef();
        // NPE free
        if(pipelineDef.getStages()==null){
            pipelineDef.setStages( new ModelASTStages( this ) );
        }
        // pre builders first

        Iterator<Builder> builderIterator = mavenModuleSet.getPrebuilders().iterator();
        while(builderIterator.hasNext())
        {
            Builder builder = builderIterator.next();
            BuilderConverter converter = findBuilderConverter( builder );
            if(converter!=null)
            {
                pipelineDef.getStages().getStages().add(converter.convert( request, builder ));
            } else {
                // TODO save some warnings
            }
        }

        // convert the main maven build part
        if(request.isUseWithMvn()){
            pipelineDef.getStages().getStages().add( toWithMvn( request ) );
        } else {
            pipelineDef.getStages().getStages().add( toSh( request ) );
        }

        mavenModuleSet.getPostbuilders();

        mavenModuleSet.getPublishers();

        mavenModuleSet.getReporters();
    }

    public ModelASTStage toWithMvn( ConverterRequest converterRequest)
    {
        MavenModuleSet mavenModuleSet = (MavenModuleSet) converterRequest.getJob();

        ModelASTStage stage = new ModelASTStage(null);

        Label label = mavenModuleSet.getAssignedLabel();
        if(label != null)
        {
            ModelASTAgent agent = new ModelASTAgent( this );
            ModelASTKey agentKey = new ModelASTKey( this );
            agentKey.setKey( "'" + label.getName() + "'" );

            agent.setAgentType( agentKey );
            stage.setAgent( agent );
        }

        stage.setName( "Maven Build" );
        ModelASTBranch branch = new ModelASTBranch( null);
        branch.setName( "Branch 1" );

        ModelASTTreeStep withMvn = new ModelASTTreeStep( null);
        withMvn.setName( "withMvn" );

        ModelASTNamedArgumentList stepArgs = new ModelASTNamedArgumentList( null);
        Map<ModelASTKey, ModelASTValue> args = new HashMap<>();
        ModelASTKey key = new ModelASTKey(this);
        key.setKey( "withMvnKey" );
        ModelASTValue value = ModelASTValue.fromConstant( "withMvnArgs", this);
        args.put( key, value);

        stepArgs.setArguments( args );
        withMvn.setArgs( stepArgs );

        ModelASTStep sh = new ModelASTStep( null);
        sh.setName( "sh" );
        withMvn.getChildren().add( sh );
        ModelASTSingleArgument singleArgument = new ModelASTSingleArgument( this);
        singleArgument.setValue( ModelASTValue.fromConstant( mavenModuleSet.getGoals(), null ));
        sh.setArgs( singleArgument );

        branch.getSteps().add( withMvn );

        stage.getBranches().add( branch );

        return stage;
    }

    public ModelASTStage toSh(ConverterRequest converterRequest)
    {
        return null;
    }

    @Override
    public boolean canConvert( Job job )
    {
        return job instanceof MavenModuleSet;
    }

    public BuilderConverter findBuilderConverter(Builder builder)
    {
        Iterator<BuilderConverter> builderConverterIterator =
            Jenkins.getInstance().getExtensionList( BuilderConverter.class ).iterator();

        while ( builderConverterIterator.hasNext() )
        {
            BuilderConverter converter = builderConverterIterator.next();
            if(converter.canConvert( builder )){
                return converter;
            }
        }
        return null;
    }
}
