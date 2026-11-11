(function(root){
 'use strict';
 const clone=x=>JSON.parse(JSON.stringify(x));
 const dayMs=86400000;
 const asDate=s=>new Date(s+'T00:00:00Z');
 const iso=d=>d.toISOString().slice(0,10);
 const days=(a,b)=>Math.round((asDate(a)-asDate(b))/dayMs);
 const nextDay=s=>iso(new Date(asDate(s).getTime()+dayMs));
 const validDate=s=>typeof s==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(s)&&!Number.isNaN(asDate(s).getTime())&&iso(asDate(s))===s;
 const validTime=s=>typeof s==='string'&&/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(s);
 function endDate(start,months){
   const d=asDate(start), day=d.getUTCDate();
   const last=new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+Number(months)+1,0));
   const target=new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+Number(months),Math.min(day,last.getUTCDate())));
   return iso(new Date(target.getTime()-dayMs));
 }
 const status=(m,today)=>today<m.start_date?'Upcoming':today>m.end_date?'Expired':'Active';
 const sessionStatus=(s,now)=>s.status==='Cancelled'?'Cancelled':s.session_date+'T'+s.end_time<=now?'Completed':'Scheduled';
 const notStarted=(s,now)=>s.status==='Scheduled'&&s.session_date+'T'+s.start_time>now;
 const overlap=(a,b)=>a.start_time<b.end_time&&b.start_time<a.end_time;
 const need=(v,msg)=>{if(!v)throw Error(msg);};
 const unique=(rows,key,value,id)=>!rows.some(r=>r.id!==id&&String(r[key]||'').toLowerCase()===String(value).toLowerCase());
 const phone=v=>/^\+?[0-9]{9,15}$/.test(v);
 const email=v=>/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
 const id=rows=>Math.max(0,...rows.map(r=>r.id))+1;
 const latest=(db,memberId)=>db.memberships.filter(m=>m.member_id===Number(memberId)).sort((a,b)=>b.end_date.localeCompare(a.end_date))[0];
 const seed={
 members:[
 [1,'Nguyen Van An','Male','1995-03-12','0901000001','an.nguyen@example.com','District 1','Build muscle','','Active'],
 [2,'Tran Thi Binh','Female','1998-07-21','0901000002','binh.tran@example.com','District 3','Lose weight','','Active'],
 [3,'Le Van Cuong','Male','1990-11-05','0901000003','','','','','Active'],
 [4,'Pham Thi Dung','Female','2000-01-30','0901000004','dung.pham@example.com','','Improve stamina','','Active'],
 [5,'Hoang Van Em','Male','1992-09-17','0901000005','em.hoang@example.com','District 7','Build muscle','Prefers evening sessions','Active'],
 [6,'Vo Thi Phuong','Female','1997-04-08','0901000006','phuong.vo@example.com','','Flexibility','','Active'],
 [7,'Dang Van Giang','Male','1988-12-25','0901000007','','','','Left the center','Inactive'],
 [8,'Bui Thi Hoa','Female','2001-06-14','0901000008','hoa.bui@example.com','','Stay fit','','Active']
 ].map(r=>Object.fromEntries(['id','full_name','gender','date_of_birth','phone','email','address','training_objective','internal_note','status'].map((k,i)=>[k,r[i]]))),
 plans:[
 {id:1,name:'Monthly',duration_months:1,price:500000,benefits:'Full gym access',status:'Available'},
 {id:2,name:'Quarterly',duration_months:3,price:1350000,benefits:'Full gym access',status:'Available'},
 {id:3,name:'Annual',duration_months:12,price:4800000,benefits:'Full gym access',status:'Available'},
 {id:4,name:'Student 6-Month',duration_months:6,price:2400000,benefits:'Full gym access, off-peak hours',status:'Unavailable'}],
 trainers:[
 {id:1,full_name:'Ngo Minh Khoa',phone:'0902000001',email:'khoa.ngo@example.com',specialization:'Strength training',years_of_experience:6,status:'Active'},
 {id:2,full_name:'Ly Thu Lan',phone:'0902000002',email:'lan.ly@example.com',specialization:'Yoga',years_of_experience:4,status:'Active'},
 {id:3,full_name:'Do Quang Minh',phone:'0902000003',email:'minh.do@example.com',specialization:'Cardio',years_of_experience:3,status:'Inactive'}],
 memberships:[
 [1,1,2,'2026-10-01','2026-12-31',1350000,null], [2,2,1,'2026-10-15','2026-11-14',500000,null],
 [3,3,1,'2026-09-20','2026-10-19',500000,null], [4,4,1,'2026-06-01','2026-06-30',500000,null],
 [5,5,1,'2026-10-13','2026-11-12',500000,null], [6,6,3,'2026-11-20','2027-11-19',4800000,null],
 [7,7,2,'2026-03-01','2026-05-31',1350000,null], [8,5,1,'2026-11-13','2026-12-12',500000,5]
 ].map(r=>Object.fromEntries(['id','member_id','plan_id','start_date','end_date','price_at_registration','previous_membership_id'].map((k,i)=>[k,r[i]]))),
 sessions:[
 [1,1,1,'2026-11-10','07:00','08:00','Chest and back','Scheduled'],
 [2,1,1,'2026-11-10','08:00','09:00','Legs','Scheduled'],
 [3,1,1,'2026-11-10','07:30','08:30','Replaced by two later sessions','Cancelled'],
 [4,2,2,'2026-11-11','18:00','19:00','Yoga basics','Scheduled'],
 [5,5,1,'2026-11-12','18:00','19:00','','Scheduled'],
 [6,3,1,'2026-10-05','17:00','18:00','','Scheduled'],
 [7,5,3,'2026-11-13','10:00','11:00','Cardio','Scheduled'],
 [8,6,2,'2026-11-21','09:00','10:00','First session','Scheduled']
 ].map(r=>Object.fromEntries(['id','member_id','trainer_id','session_date','start_time','end_time','note','status'].map((k,i)=>[k,r[i]]))),
 accounts:[{id:1,username:'admin',full_name:'System Admin',role:'Admin',status:'Active',must_change_password:true},{id:2,username:'staff01',full_name:'Front Desk Staff',role:'Staff',status:'Active',must_change_password:true}]
 };
 seed.members.forEach(m=>seed.accounts.push({id:id(seed.accounts),username:m.phone,full_name:m.full_name,role:'Member',status:m.status==='Active'?'Active':'Locked',member_id:m.id,must_change_password:true}));
 seed.trainers.forEach(t=>seed.accounts.push({id:id(seed.accounts),username:t.phone,full_name:t.full_name,role:'Trainer',status:t.status==='Active'?'Active':'Locked',trainer_id:t.id,must_change_password:true}));
 function saveProfile(db,type,values,today,confirmDuplicate=false){
   const rows=db[type], v=clone(values), old=rows.find(x=>x.id===v.id);
   v.full_name=String(v.full_name||'').trim();v.phone=String(v.phone||'').trim();v.email=String(v.email||'').trim();
   need(v.full_name&&v.full_name.length<=100,'Enter a full name of up to 100 characters.');
   need(phone(v.phone),'Use 9 to 15 digits for the phone number.');
   need(unique(rows,'phone',v.phone,v.id),'This phone number already exists.');
   need(!v.email||email(v.email),'Enter a valid email address.');
   need(!v.email||unique(rows,'email',v.email,v.id),'This email address already exists.');
   need(['Active','Inactive'].includes(v.status),'Choose a valid profile status.');
   if(type==='members'){
     need(['Male','Female','Other'].includes(v.gender),'Select a gender.');
     need(validDate(v.date_of_birth)&&v.date_of_birth<today,'Date of birth must be a valid past date.');
     if(!confirmDuplicate&&rows.some(x=>x.id!==v.id&&x.full_name.toLowerCase()===v.full_name.toLowerCase()&&x.date_of_birth===v.date_of_birth))throw Error('POSSIBLE_DUPLICATE');
   }else{
     need(v.email,'Trainer email is required.');
     v.years_of_experience=v.years_of_experience===''?null:Number(v.years_of_experience);
     need(v.years_of_experience===null||Number.isInteger(v.years_of_experience)&&v.years_of_experience>=0,'Experience must be a non-negative whole number.');
   }
   if(!old){need(unique(db.accounts,'username',v.phone),'This phone is already used as an account username.');v.id=id(rows);rows.push(v);
     db.accounts.push({id:id(db.accounts),username:v.phone,full_name:v.full_name,role:type==='members'?'Member':'Trainer',status:v.status==='Active'?'Active':'Locked',must_change_password:true,[type==='members'?'member_id':'trainer_id']:v.id});
   }else{const changedStatus=old.status!==v.status;Object.assign(old,v); const account=db.accounts.find(a=>a[type==='members'?'member_id':'trainer_id']===v.id);if(account&&changedStatus)account.status=v.status==='Active'?'Active':'Locked';}
   return v;
 }
 function savePlan(db,v){
   v=clone(v);v.name=String(v.name||'').trim();v.duration_months=Number(v.duration_months);v.price=Number(v.price);
   need(v.name&&v.name.length<=100,'Enter a plan name of up to 100 characters.');need(unique(db.plans,'name',v.name,v.id),'Plan name must be unique.');
   need(Number.isInteger(v.duration_months)&&v.duration_months>0&&v.duration_months<=120,'Duration must be 1 to 120 whole months in this prototype.');
   need(Number.isFinite(v.price)&&v.price>0&&v.price<1e10,'Enter a positive price below 10 billion VND.');need(String(v.benefits||'').trim(),'Enter the plan benefits.');
   need(['Available','Unavailable'].includes(v.status),'Choose a valid plan status.');
   const old=db.plans.find(x=>x.id===v.id);if(old)Object.assign(old,v);else{v.id=id(db.plans);db.plans.push(v);}return v;
 }
 function registrationPreview(db,memberId,planId,start,today){
   const member=db.members.find(x=>x.id===Number(memberId)),plan=db.plans.find(x=>x.id===Number(planId));
   need(member&&member.status==='Active','Choose an Active member.');need(plan&&plan.status==='Available','Choose an Available plan.');
   const last=latest(db,member.id);let previous=null;
   if(last){const st=status(last,today);need(st==='Expired'||st==='Active'&&days(last.end_date,today)<=7,'The latest membership is Upcoming or Active with more than 7 days remaining.');previous=last.id;if(st==='Active')start=nextDay(last.end_date);}
   need(validDate(start)&&start>=today,'Start date must be a valid date today or later.');
   const end=endDate(start,plan.duration_months);
   need(!db.memberships.some(m=>m.member_id===member.id&&start<=m.end_date&&m.start_date<=end),'This period overlaps an existing membership.');
   return {member_id:member.id,plan_id:plan.id,start_date:start,end_date:end,price_at_registration:plan.price,previous_membership_id:previous};
 }
 function register(db,...args){const v=registrationPreview(db,...args);v.id=id(db.memberships);db.memberships.push(v);return v;}
 function validateSession(db,v,now){
   const today=now.slice(0,10);v.member_id=Number(v.member_id);v.trainer_id=Number(v.trainer_id);
   const member=db.members.find(x=>x.id===v.member_id),trainer=db.trainers.find(x=>x.id===v.trainer_id);
   need(member&&member.status==='Active','Choose an Active member.');need(trainer&&trainer.status==='Active','Choose an Active trainer.');
   need(validDate(v.session_date)&&v.session_date>=today,'Session date must be a valid date today or later.');
   need(v.session_date+'T'+v.start_time>now,'Session start must be later than the demo clock.');
   const minutes=s=>Number(s.slice(0,2))*60+Number(s.slice(3,5)),duration=minutes(v.end_time)-minutes(v.start_time);
   need(validTime(v.start_time)&&validTime(v.end_time)&&duration>=30&&duration<=120,'Use valid times and a duration of 30 to 120 minutes.');
   need(v.start_time>='05:00'&&v.end_time<='22:00','Sessions must be within 05:00 to 22:00.');
   need(db.memberships.some(m=>m.member_id===v.member_id&&m.start_date<=v.session_date&&v.session_date<=m.end_date),'No membership covers this session date. Register or renew first.');
   const same=db.sessions.filter(s=>s.id!==v.id&&s.status!=='Cancelled'&&s.session_date===v.session_date&&overlap(s,v));
   need(!same.some(s=>s.member_id===v.member_id),'Member has an overlapping session.');need(!same.some(s=>s.trainer_id===v.trainer_id),'Trainer has an overlapping session.');return v;
 }
 function saveSession(db,values,now){
   const v=clone(values),old=db.sessions.find(x=>x.id===v.id);if(old)need(notStarted(old,now),'Only a Scheduled session that has not started can be edited.');
   validateSession(db,v,now);v.status='Scheduled';if(old)Object.assign(old,v);else{v.id=id(db.sessions);db.sessions.push(v);}return v;
 }
 function cancelSession(db,sessionId,now){const s=db.sessions.find(x=>x.id===Number(sessionId));need(s&&notStarted(s,now),'Only a Scheduled session that has not started can be cancelled.');s.status='Cancelled';}
 function expirations(db,today){return db.memberships.filter(m=>{
   const delta=days(m.end_date,today),st=status(m,today);
   return (st==='Active'&&delta<=7||st==='Expired'&&delta>=-30)&&!db.memberships.some(n=>n.member_id===m.member_id&&n.start_date>m.end_date&&status(n,today)==='Upcoming');
 });}
 function saveAccount(db,v){
   v=clone(v);v.username=String(v.username||'').trim();v.full_name=String(v.full_name||'').trim();
   need(v.username&&v.full_name,'Username and full name are required.');need(unique(db.accounts,'username',v.username,v.id),'Username must be unique.');need(['Admin','Staff'].includes(v.role),'Only Admin and Staff accounts can be created here.');
   v.id=id(db.accounts);v.status='Active';v.must_change_password=true;db.accounts.push(v);return v;
 }
 function toggleAccount(db,accountId){const a=db.accounts.find(x=>x.id===Number(accountId));need(a,'Account not found.');
   if(a.status==='Active'&&a.role==='Admin')need(db.accounts.filter(x=>x.role==='Admin'&&x.status==='Active').length>1,'At least one Active Admin must remain.');
   if(a.status==='Locked'&&(a.member_id||a.trainer_id)){const p=(a.member_id?db.members:db.trainers).find(x=>x.id===(a.member_id||a.trainer_id));need(p.status==='Active','Reactivate the linked profile before unlocking its account.');}
   a.status=a.status==='Active'?'Locked':'Active';
 }
 const API={clone,seed,days,nextDay,endDate,status,sessionStatus,notStarted,overlap,latest,saveProfile,savePlan,registrationPreview,register,validateSession,saveSession,cancelSession,expirations,saveAccount,toggleAccount};
 if(typeof module!=='undefined')module.exports=API;else root.Gym=API;
})(typeof window!=='undefined'?window:globalThis);
