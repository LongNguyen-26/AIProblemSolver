#include <bits/stdc++.h>
using namespace std;

int main(){
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int T; if(!(cin>>T)) return 0;
    while(T--){
        int n;cin>>n;
        vector<long long> t(n), v(n), blown(n,0);
        long long maxT=0;
        for(int i=0;i<n;i++){
            cin>>t[i]>>v[i];
            maxT=max(maxT,t[i]);
        }
        // naive simulation: for each second from 0 to maxT-1, pick balloon with largest current amount that is still dispatchable
        for(long long cur=0; cur<maxT; ++cur){
            long long best=-1; int idx=-1;
            for(int i=0;i<n;i++){
                if(t[i]>cur){ // still can be blown
                    long long curAmt=v[i]+blown[i];
                    if(curAmt>best){
                        best=curAmt; idx=i;
                    }
                }
            }
            if(idx!=-1){
                ++blown[idx];
            }
        }
        __int128 total=0;
        for(int i=0;i<n;i++){
            long long finalAmt=v[i]+blown[i];
            total+= (__int128)finalAmt*finalAmt;
        }
        long long out=(long long)total; // fits in 64-bit for given constraints
        cout<<out<<"\n";
    }
    return 0;
}
