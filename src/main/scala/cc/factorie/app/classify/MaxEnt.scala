/* Copyright (C) 2008-2010 University of Massachusetts Amherst,
   Department of Computer Science.
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package cc.factorie.app.classify

import cc.factorie._
import la.{Tensor1, Tensor}

// TODO should really change this name since the SampleRank objective is not the MaxEnt objective -luke
// I agree, but then MaxEntLikelihoodTrainer should be changed also. -akm
// People talk about "MaxEnt" classifiers... we should think about what they really mean. -akm
class MaxEntSampleRankTrainer(val optimizer:optimize.GradientOptimizer = new optimize.MIRA, implicit val random: scala.util.Random) extends ClassifierTrainer {
  var iterations = 10
  var learningRateDecay = 0.9
  def train[L <: LabeledMutableDiscreteVar[_], F <: DiscreteTensorVar](il: LabelList[L, F]): Classifier[L] = {
    val cmodel = new LogLinearModel(il.labelToFeatures, il.labelDomain, il.instanceDomain)(il.labelManifest, il.featureManifest)
    val sampler = new GibbsSampler(cmodel, HammingObjective) {
      override def pickProposal(proposals: Seq[Proposal]): Proposal = proposals.head // which proposal is picked is irrelevant, so make it quick
    }
    val learner = new optimize.SampleRankTrainer(sampler, optimizer)
    learner.processContexts(il, iterations)
    new ModelBasedClassifier(cmodel, il.head.domain)
  }
}

class MaxEntLikelihoodTrainer(val variance: Double = 10.0, val warmStart: Tensor = null) extends ClassifierTrainer {
  def train[L <: LabeledMutableDiscreteVar[_], F <: DiscreteTensorVar](il: LabelList[L, F]): Classifier[L] = {
    val cmodel = new LogLinearModel(il.labelToFeatures, il.labelDomain, il.instanceDomain)(il.labelManifest, il.featureManifest)
    val pieces = il.map(l => new optimize.LinearMultiClassExample(
      cmodel.evidenceTemplate.weights,
      il.labelToFeatures(l).value.asInstanceOf[Tensor1],
      l.intValue,
      optimize.LinearObjectives.logMultiClass,
      weight = il.instanceWeight(l)))
    if (warmStart != null) cmodel.evidenceTemplate.weights.value := warmStart
    // Do the training by BFGS
    val lbfgs = new optimize.LBFGS with optimize.L2Regularization { variance = variance }
    val strategy = new optimize.BatchTrainer(cmodel.parameters, lbfgs)
    while (!strategy.isConverged)
      strategy.processExamples(pieces)
    new ModelBasedClassifier(cmodel, il.head.domain)
  }
}

// The default trainer
class MaxEntTrainer extends MaxEntLikelihoodTrainer()

class GeneralClassifierTrainer[L<: LabeledMutableDiscreteVar[_], F<:DiscreteTensorVar](
  val trainerMaker: WeightsSet => optimize.Trainer, val objective: optimize.LinearObjectives.MultiClass)  {
  def train(il: LabelList[L, F]) = {
    val cmodel = new LogLinearModel(il.labelToFeatures, il.labelDomain, il.instanceDomain)(il.labelManifest, il.featureManifest)
    val trainer = trainerMaker(cmodel.parameters)
    val examples = il.map(l => new optimize.LinearMultiClassExample(
      cmodel.evidenceTemplate.weights,
      il.labelToFeatures(l).tensor.asInstanceOf[Tensor1],
      l.intValue,
      objective,
      weight = il.instanceWeight(l)))
    while (!trainer.isConverged)
      trainer.processExamples(examples)
    new ModelBasedClassifier(cmodel, il.head.domain)
  }
}