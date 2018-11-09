


setwd("D:/AProjects/VELMA_results/Longfellow_10m_MOEA/")

out = read.csv('1stmoeaOutput.csv')

#Figure 1 
plot(-out$runoff_NSE_G,main='NSE Evolution',xlab='simulation',ylab='NSE')

#Figure 2 
par(mfrow=c(2,3))
plot(-out$runoff_NSE_G,out$X.calibration.VelmaCalibration.properties.be,
     xlim=c(-1,1),ylim=c(1,15),ylab='be',xlab='NSE',pch=20,cex=1.4)
plot(-out$runoff_NSE_G,out$X.cover.Cover1_Conifer.petParam1,
     xlim=c(-1,1),ylim=c(0,1),ylab='conifer_petparam1',xlab='NSE',pch=20,cex=1.4)
plot(-out$runoff_NSE_G,out$X.cover.Cover3_Grass.petParam1,
     xlim=c(-1,1),ylim=c(0,1),ylab='grass_petparam1',xlab='NSE',pch=20,cex=1.4)
plot(-out$runoff_NSE_G,out$X.soil.Soil3_sandy_loam.ksLateralExponentialDecayFactor,
     xlim=c(-1,1),ylim=c(0.000775,0.002325),ylab='ksLat',xlab='NSE',pch=20,cex=1.4)
plot(-out$runoff_NSE_G,out$X.soil.Soil3_sandy_loam.ksVerticalExponentialDecayFactor,
     xlim=c(-1,1),ylim=c(0.00065,0.00195),ylab='ksVert',xlab='NSE',pch=20,cex=1.4)
plot(-out$runoff_NSE_G,out$X.soil.Soil3_sandy_loam.surfaceKs,
     xlim=c(-1,1),ylim=c(250,1000),ylab='surfaceKs',xlab='NSE',pch=20,cex=1.4)




#OK Bring in the second set of MOEA output values. 
#For some reason, it doesn't like my header. 
out2 = read.csv('3rdmoeaOutput.csv',header=F,skip=1)
#Figure 1 
plot(-out2$V2,main='NSE Evolution',xlab='simulation',ylab='NSE',ylim=c(-1,1))


#Figure 2 
par(mfrow=c(2,3))
plot(-out2$V2,out2$V4,
     xlim=c(0,1),ylim=c(1,15),ylab='be',xlab='NSE',pch=20,cex=1.4)
plot(-out2$V2,out2$V5,
     xlim=c(0,1),ylim=c(0,1),ylab='conifer_petparam1',xlab='NSE',pch=20,cex=1.4)
plot(-out2$V2,out2$V6,
     xlim=c(0,1),ylim=c(0,1),ylab='grass_petparam1',xlab='NSE',pch=20,cex=1.4)
plot(-out2$V2,out2$V7,
     xlim=c(0,1),ylim=c(0.0000775,0.002325),ylab='ksLat',xlab='NSE',pch=20,cex=1.4)
plot(-out2$V2,out2$V8,
     xlim=c(0,1),ylim=c(0.00065,0.004),ylab='ksVert',xlab='NSE',pch=20,cex=1.4)
plot(-out2$V2,out2$V9,
     xlim=c(0,1),ylim=c(250,1000),ylab='surfaceKs',xlab='NSE',pch=20,cex=1.4)





#comparing sim and obs
sim62 = read.csv("flow_0d62.csv")
sim56 = read.csv("flow_0d56.csv")
sim62irr = read.csv("flow_0d62_withIrr.csv")
sim62$date = as.Date(sim62$date,'%m/%d/%Y')
sim56$date = as.Date(sim56$date,'%m/%d/%Y')
sim62irr$date = as.Date(sim62irr$date,'%m/%d/%Y')


temp = read.csv('obs_098A.csv',header=F)
obs = data.frame(
  as.Date(paste(temp$V1,'-',temp$V2,sep=''),'%Y-%j'),
  temp$V3)
names(obs) <- c('date','obs_flow')


#plotting daily flow
plot(sim62$date,sim62$flow_mm_day,type='l',
     xlim=c(as.Date('2013-10-01'),as.Date('2014-10-01')),
     ylim=c(0,10),
     xlab='',ylab='Discharge (mm/day)')
lines(obs$date,obs$obs_flow,col='blue')
legend('topright',c('simulated','observed'),lty=c(1,1),col=c('black','blue'))
lines(sim62irr$date,sim62irr$flow_0d62_irr_mm_day,col='red')
legend('topright',c('simulated','observed','sim_irr'),lty=c(1,1,1),col=c('black','blue','red'))

plot(sim62$date,sim62irr$flow_0d62_irr_mm_day-sim62$flow_mm_day,type='l',col='blue',
     xlim=c(as.Date('2013-05-01'),as.Date('2014-05-01')),
     ylim=c(-0.2,.2),
     xlab='',ylab='Discharge Delta (mm/day)',main="With Irr - WithoutIrr")
abline(h=0)

lines(obs$date,obs$obs_flow,col='blue')
legend('topright',c('simulated','observed'),lty=c(1,1),col=c('black','blue'))
lines(sim62irr$date,sim62irr$flow_0d62_irr_mm_day,col='red')
legend('topright',c('simulated','observed','sim_irr'),lty=c(1,1,1),col=c('black','blue','red'))



plot(sim62$date,sim62$flow_mm_day,type='l',col='black',
     xlim=c(as.Date('2013-1-01'),as.Date('2014-12-31')),
     ylim=c(0,10),
     xlab='',ylab='Discharge (mm/day)')
lines(obs$date,obs$obs_flow,col='blue')
legend('topright',c('simulated','observed'),lty=c(1,1),col=c('black','blue'))
lines(sim62irr$date,sim62irr$flow_0d62_irr_mm_day,col='red')


#plotting sum of monthly flow
sim62_monthly = aggregate(sim62$flow_mm_day,by=list(format(sim62$date,'%Y-%m')),FUN=sum)
obs_monthly = aggregate(obs$obs_flow,by=list(format(obs$date,'%Y-%m')),FUN=sum)
#remove incomplete month of May
obs_monthly = obs_monthly[1:6,]

plot(seq(as.Date('2012-01-01'),as.Date('2014-12-01'),by='month'),sim62_monthly$x,type='b',
     ylim=c(0,100),
     xlab='',ylab='Monthly Flow Sum (mm)')
lines(seq(as.Date('2013-11-01'),as.Date('2014-04-01'),by='month'),obs_monthly$x,col='orange',pch=19,type='b')

###trim to get nash-sutcliffe's. 
monthly_compare = data.frame(
  obs_monthly$Group.1,
  obs_monthly$x,
  sim62_monthly$x[sim62_monthly$Group.1 %in% obs_monthly$Group.1]
)
names(monthly_compare)<-c('date','obs','sim')

library(hydroGOF)
gof(sim=monthly_compare$sim,obs=monthly_compare$obs)
#ME       0.67
#MAE      6.60
#MSE     58.71
#RMSE     7.66
#NRMSE % 50.50
#PBIAS %  2.80
#RSR      0.51
#rSD      1.35
#NSE      0.69
#mNSE     0.44
#rNSE     0.75
#d        0.94
#md       0.74
#rd       0.95
#cp       0.78
#r        0.93
#R2       0.87
#bR2      0.80
#KGE      0.65
#VE       0.72

##ANALYSIS OF DAILY METRICS
simtest = sim62[sim62$date %in% obs$date,]
obstest = obs$obs_flow
gof(sim=simtest$flow_mm_day,obs=obstest)
#ME       0.02
#MAE      0.34
#MSE      0.33
#RMSE     0.57
#NRMSE % 61.90
#PBIAS %  2.90
#RSR      0.62
#rSD      0.96
#NSE      0.61
#mNSE     0.49
#rNSE     0.71
#d        0.89
#md       0.74
#rd       0.92
#cp       0.55
#r        0.80
#R2       0.64
#bR2      0.56
#KGE      0.80
#VE       0.58





