
HasSubject : AbstractPlayer {
	
	var <>subject;

	*new { arg subject=0.0;
		^super.new.subject_(subject.loadDocument)
	}

	ar { ^subject.ar }
	
	storeParamsOn { arg stream;
		stream.storeArgs([enpath(subject)]);
	}
	stop { super.stop; subject.stop }
	children { ^[subject] }
	numChannels { ^subject.tryPerform(\numChannels) ? 1 }
	guiClass { ^HasSubjectGui }
}

/*
Ar : HasSubject { 

	ar {  // same as .value
		if(subject.respondsTo(\ar),{
			^subject.ar
		},{
			^Plug.ar(subject)
		})
	}
	
	next { ^this.value }
	
	doesNotUnderstand { arg selector ... args;
		^subject.performList(selector,args)
	}
	// dodgy
	canDoSpec { arg aspec; ^(aspec.isKindOf(AudioSpec) or: {aspec.canKr}) } // could go into any kr too

}

AsStream : HasSubject {  // this is for passing in patterns or streams to Patches

	ar {
		^subject.asStream
	}
	
	next { ^this.value }

	doesNotUnderstand { arg selector ... args;
		^subject.performList(selector,args)
	}

}

*/

/*

Kr : Ar { // should all be moved under KrPlayer

	var <>lag;
	
	*new { arg subject=0.0,lag=0.0;
		^super.new(subject).lag_(lag)
	}
	value { ^this.kr }
	ar { ^K2A.ar(this.kr) } 
	kr {
		if(subject.respondsTo(\kr),{
			^subject.kr(lag.value)
		},{
			^Plug.kr(subject.asStream,lag.value)  //  'subject' could be a number, function or .value responding object
		})
	}
	spec_ { arg aspec; // respondsTo
		subject.tryPerform('spec_',aspec);
	}
	value_ { arg val;
		subject.tryPerform('value_',val);
	}
	canDoSpec {arg aspec; ^aspec.canKr }
	storeParamsOn { arg stream;
		stream.storeArgs([ enpath( subject ),lag ]);	
	}
	children { ^[subject,lag] }

	guiClass { ^KrGui }
	
}

*/



/*

StreamKr : HasSubjectKr { // trigger is a kr rate trigger
	
	var <>trigger;
	
	*new { arg subject=0.0,trigger=0.0;
		^super.new(subject).trigger_(trigger)
	}
//	kr { //sc2 version
//		var k;
//		k=Sequencer.kr(subject.asStream,trigger.value);
//	}
	
	storeParamsOn { arg stream;
		stream.storeArgs([enpath(subject),trigger]);
	}
	children { ^[subject,trigger] }
	guiClass { ^StreamKrGui }
	
}

*/




StreamKrDur : HasSubject { // Synthless, above player

	var <>durations,<>lag;
	var tempo,routine,bus;
	
	*new { arg values=0.0,durations=0.0,lag=0.0;
		// check if can become a BufSeqDur

		// make refs of arrays into Pseq
		if(values.isKindOf(Ref),{
			values = Pseq(values.value,inf);
		});
		if(durations.isKindOf(Ref),{
			durations = Pseq(durations.value,inf);
		});
		^super.new(values).durations_(durations.loadDocument).lag_(lag).skdinit
	}
	skdinit {
		tempo = Tempo.default;
		/* for musical accuracy, need to run ahead and use OSCSched
		to deliver.
		changing tempo while playing will screw it up and sync will be
		lost between other StreamKr.
		*/
		routine = Routine({
					var dur,val;
					var valst,durst;
					valst = subject.asStream;
					durst = durations.asStream;
					while({
						(val = valst.next).notNil and:
							(dur = durst.next).notNil;
					},{
						// send the message...	
						bus.value_(val);
						tempo.beats2secs(dur).wait
					});
				});
	}
	// has no synth, just a bus
	loadDefFileToBundle {}
	rate { ^\control }
	children { ^[] } // inputs
	spawnAtTime { arg atTime;
		// depending on didSpawn to start for now
	}
	spawnToBundle {
		this.spawnAtTime; // for now
	}
	didSpawn {	arg patchIn,synthi;
		patchOut.connectTo(patchIn,false);
		bus = patchOut.bus;
		routine.reset;
		SystemClock.play(routine)
	}
	instrArgRate { ^\control }
	instrArgFromControl { arg control;
		^if(lag == 0.0,{
			In.kr(control,1)
		},{
			Lag.kr(In.kr(control,1),lag)
		})
	}
	synthArg {
		^patchOut.synthArg
	}
	stop { routine.stop }
	free {
		routine.stop;
		//routine.reset;
	}
	// shouldn't happen
	synthDefArgs {}
	asSynthDef {}
	
	
	
	/*
	// for BufSeqDur
	prepareToBundle { arg group,bundle;
		var values,durs,data;
		super.prepareToBundle(group,bundle);
		values = subject.oneCycle;
		durs = durations.oneCycle;
		minTime = durs.minItem;
		data = Array.new;
		durs.do({ arg d,i;
			
		})
			
		buffer = subject.asBufferToBundle(group,bundle);
		
		tempoBus = TempoBus.new;
		tempoBus.prepareToBundle(group,bundle);
	}

	synthDefArgs { ^[buffer.synthArg,tempoBus.synthArg,framesPerBeat,patchOut.synthArg] }
	asSynthDef {
		^SynthDef(this.defName,{ arg i_bufnum=0, i_tempoBusIndex=0,i_framesPerBeat=4.0,out=0;
			var phasor,tempo,numFrames,value,cpb;
			
			tempo = In.kr(i_tempoBusIndex) * i_framesPerBeat;

			numFrames = BufFrames.kr(i_bufnum);
			cpb = i_framesPerBeat / numFrames;
			phasor = LFSaw.kr(tempo / cpb).range(0,numFrames);
			value = Index.kr(i_bufnum, phasor);
			Out.kr(out,
				value
			)
		})
	}
	
	
	kr { 
		var k,t;
		k=Sequencer.kr(subject.asStream,StepClock.kr(trigger.asStream,GetTempo.kr));
		^if(lag != 0.0,{
			Lag.kr(k,lag.value)
		},{
			k
		})
	}
	
	*/
}




Stream2Trig : StreamKrDur { // outputs just a single pulse trig

	// doesn't use lag	
	instrArgFromControl { arg control;
		^InTrig.kr( control,1)
	}
	
//	kr { //sc2 version
//		^ImpulseSequencer.kr(subject.asStream,StepClock.kr(trigger.asStream,GetTempo.kr));
//	}
}


